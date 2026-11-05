import java.io.IOException
import java.net.Socket

plugins {
    id("java")
}

group = "org.spring"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.junit:junit-bom:6.0.0"))
    implementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Source: https://mvnrepository.com/artifact/io.rest-assured/rest-assured
    implementation("io.rest-assured:rest-assured:6.3.0")
    // Source: https://mvnrepository.com/artifact/org.projectlombok/lombok
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    testCompileOnly("org.projectlombok:lombok:1.18.46")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.46")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.github.javafaker:javafaker:1.0.2")

    // Allure: JUnit-адаптер пишет результаты тестов, rest-assured фильтр прикладывает HTTP-запросы и ответы
    implementation("io.qameta.allure:allure-rest-assured:2.29.0")
    testImplementation("io.qameta.allure:allure-junit5:2.29.0")
}

// ---------------------------------------------------------------- запуск тестов по JUnit-тегам
//   ./gradlew test                    все тесты
//   ./gradlew test -Ptags=smoke,e2e   тесты с любым из перечисленных тегов
//   ./gradlew smokeTest               быстрые проверки основных сценариев
//   ./gradlew regressionTest          полная функциональная проверка (smoke входит)
//   ./gradlew e2eTest                 сквозной сценарий user service + course service

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8" // исходники с русскими DisplayName
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false } // API-тесты всегда гоняем заново, без кэша Gradle
}

tasks.test {
    useJUnitPlatform {
        (project.findProperty("tags") as String?)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.let { includeTags(*it.toTypedArray()) }
    }
}

fun registerTagTask(taskName: String, tag: String, about: String) =
    tasks.register<Test>(taskName) {
        group = "verification"
        description = about
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform {
            includeTags(tag)
        }
    }

registerTagTask("smokeTest", "smoke", "Smoke: быстрые проверки основных сценариев")
registerTagTask("regressionTest", "regression", "Regression: все функциональные тесты сервисов")
registerTagTask("e2eTest", "e2e", "E2E: сквозной сценарий user service + course service")

// ---------------------------------------------------------------- Allure
// После ЛЮБОГО запуска тестов (test, smokeTest, regressionTest, e2eTest, даже если тесты упали)
// отчёт собирается в build/reports/allure-report и открывается в браузере.
//   ./gradlew smokeTest                    тесты + отчёт + браузер
//   ./gradlew smokeTest -PnoAllureOpen     то же, но без браузера (для CI): отчёт только в build/reports/allure-report
//   ./gradlew allureReport                 собрать отчёт из уже имеющихся результатов
//   ./gradlew allureServe                  собрать и открыть отчёт в браузере вручную (блокирует Gradle до Ctrl+C)
// Результаты каждого запуска пишутся в build/allure-results и перед следующим запуском очищаются.
// Отчёт открывается через локальный сервер http://localhost:8899 (он остаётся работать в фоне и
// сам подхватывает новый отчёт при следующем запуске). Allure CLI скачивается из Maven Central автоматически.
val allureVersion = "2.29.0"
val allurePort = 8899
val allureCli: Configuration = configurations.create("allureCli")

dependencies {
    allureCli("io.qameta.allure:allure-commandline:$allureVersion@zip")
}

val unpackAllureCli = tasks.register<Sync>("unpackAllureCli") {
    from({ zipTree(allureCli.singleFile) })
    into(layout.buildDirectory.dir("allure-cli"))
}

fun allureExecutable(): String {
    val bin = layout.buildDirectory.dir("allure-cli/allure-$allureVersion/bin").get().asFile
    return File(bin, if (System.getProperty("os.name").lowercase().contains("win")) "allure.bat" else "allure").absolutePath
}

tasks.register<Exec>("allureReport") {
    group = "verification"
    description = "Allure: собирает HTML-отчёт из build/allure-results в build/reports/allure-report"
    dependsOn(unpackAllureCli)
    doFirst {
        commandLine(allureExecutable(), "generate", "build/allure-results", "-o", "build/reports/allure-report", "--clean")
    }
}

tasks.register<Exec>("allureServe") {
    group = "verification"
    description = "Allure: собирает отчёт и открывает его в браузере (блокирует Gradle до Ctrl+C)"
    dependsOn(unpackAllureCli)
    doFirst {
        commandLine(allureExecutable(), "serve", "build/allure-results")
    }
}

// перед запуском тестов убираем результаты прошлого прогона, иначе в отчёте будет смесь запусков
val cleanAllureResults = tasks.register<Delete>("cleanAllureResults") {
    delete(layout.buildDirectory.dir("allure-results"))
}

// финализатор: выполняется после тестов всегда, в том числе когда тесты упали
val allureOpen = tasks.register("allureOpen") {
    group = "verification"
    description = "Allure: собирает отчёт и открывает его в браузере; запускается автоматически после любых тестов"
    dependsOn(unpackAllureCli)
    val resultsDir = layout.buildDirectory.dir("allure-results").get().asFile
    val reportDir = layout.buildDirectory.dir("reports/allure-report").get().asFile
    val logFile = layout.buildDirectory.file("allure-server.log").get().asFile
    val skipBrowser = providers.gradleProperty("noAllureOpen").isPresent
    doLast {
        if (!resultsDir.exists() || resultsDir.list().isNullOrEmpty()) {
            println("Allure: результатов нет (тесты не запускались), отчёт не собран")
            return@doLast
        }
        val exe = allureExecutable()
        val generate = ProcessBuilder(exe, "generate", resultsDir.absolutePath, "-o", reportDir.absolutePath, "--clean")
            .redirectErrorStream(true).start()
        generate.inputStream.bufferedReader().forEachLine { println("Allure: $it") }
        if (generate.waitFor() != 0) {
            println("Allure: не удалось собрать отчёт")
            return@doLast
        }
        println("Allure: отчёт собран: ${File(reportDir, "index.html").absolutePath}")
        if (skipBrowser) {
            return@doLast
        }
        val url = "http://localhost:$allurePort"
        val serverRunning = try {
            Socket("localhost", allurePort).use { true }
        } catch (e: IOException) {
            false
        }
        if (serverRunning) {
            // сервер от прошлого запуска уже отдаёт эту папку — достаточно открыть вкладку
            val windows = System.getProperty("os.name").lowercase().contains("win")
            val open = if (windows) listOf("rundll32", "url.dll,FileProtocolHandler", url)
            else listOf(if (System.getProperty("os.name").lowercase().contains("mac")) "open" else "xdg-open", url)
            ProcessBuilder(open).start()
        } else {
            // allure open поднимает сервер и сам открывает браузер; процесс остаётся в фоне
            ProcessBuilder(exe, "open", reportDir.absolutePath, "-p", allurePort.toString())
                .redirectErrorStream(true).redirectOutput(logFile).start()
        }
        println("Allure: отчёт открыт в браузере: $url")
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(cleanAllureResults)
    finalizedBy(allureOpen)
}
