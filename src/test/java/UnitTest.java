import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.UnitController;
import kg.jazzlms.models.request.UnitRequest;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.UnitResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static kg.jazzlms.utils.EntityManager.unitBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Course service: юниты (уроки) курса")
public class UnitTest {

    private static final CourseController COURSE_CONTROLLER = new CourseController(Endpoints.COURSE_URL);
    private static final UnitController UNIT_CONTROLLER = new UnitController(Endpoints.COURSE_URL);
    private static final List<UUID> COURSES = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String YOUTUBE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    private CourseResponse createCourse() {
        CourseResponse created = COURSE_CONTROLLER.createCourse(EntityManager.generateCourse());
        COURSES.add(created.getId());
        return created;
    }

    private UnitResponse createUnit(CourseResponse course, UnitRequest request) {
        return UNIT_CONTROLLER.createUnit(course.getId(), request);
    }

    private File file(String name) throws IOException {
        return Files.writeString(tempDir.resolve(name), "%PDF-1.4\n%%EOF\n").toFile();
    }

    @AfterAll
    static void cleanUp() {
        COURSES.forEach(id -> {
            COURSE_CONTROLLER.deleteCourseResponse(id);
            COURSE_CONTROLLER.deleteCoursePermanentlyResponse(id);
        });
    }

    // ---------- POST /api/courses/{id}/units: positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Юнит CONTENT -> 201, поля совпадают, position = 1, completionType по умолчанию CHECKBOX")
    void createContentUnit() {
        CourseResponse course = createCourse();
        UnitRequest request = unitBuilder().description("Описание").build();

        UnitResponse created = createUnit(course, request);

        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals(course.getId(), created.getCourseId()),
                () -> assertEquals("CONTENT", created.getType()),
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertEquals(1, created.getPosition()),
                () -> assertTrue(created.isActive()),
                () -> assertEquals("CHECKBOX", created.getCompletionType()),
                () -> assertEquals("Описание", created.getDescription()),
                () -> assertFalse(created.isCompleted())
        );
    }

    @Test
    @DisplayName("Юнит VIDEO с YouTube -> 201, embedUrl собирается из ссылки")
    void createYoutubeUnit() {
        CourseResponse course = createCourse();

        UnitResponse created = createUnit(course, UnitRequest.builder().name("Video").type("VIDEO")
                .sourceType("YOUTUBE").youtubeUrl(YOUTUBE).build());

        assertAll(
                () -> assertEquals("VIDEO", created.getType()),
                () -> assertEquals("YOUTUBE", created.getSourceType()),
                () -> assertEquals(YOUTUBE, created.getYoutubeUrl()),
                () -> assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", created.getEmbedUrl())
        );
    }

    @Test
    @DisplayName("Юнит PRESENTATION с загруженным pdf -> 201, имя файла сохраняется")
    void createUnitWithUploadedFile() throws IOException {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createUnitResponse(course.getId(),
                UnitRequest.builder().name("Slides").type("PRESENTATION").sourceType("UPLOAD").build(), file("slides.pdf"));
        assertEquals(201, response.statusCode(), response.asString());

        UnitResponse created = response.as(UnitResponse.class);
        assertAll(
                () -> assertEquals("PRESENTATION", created.getType()),
                () -> assertEquals("slides.pdf", created.getFileName())
        );
    }

    @Test
    @DisplayName("Файл появляется в GET /api/courses/{id}/files")
    void uploadedFileIsListedInCourseFiles() throws IOException {
        CourseResponse course = createCourse();
        UNIT_CONTROLLER.createUnitResponse(course.getId(),
                UnitRequest.builder().name("Slides").type("PRESENTATION").sourceType("UPLOAD").build(), file("listed.pdf"));

        Response response = COURSE_CONTROLLER.courseFilesResponse(course.getId());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(1, response.jsonPath().getList("$").size())
        );
    }

    @Test
    @DisplayName("completionType QUESTION и TIME сохраняются вместе с question, answer, timeLimitSeconds")
    void createUnitsWithEachCompletionType() {
        CourseResponse course = createCourse();

        UnitResponse question = createUnit(course, unitBuilder().completionType("QUESTION").question("2+2?").answer("4").build());
        UnitResponse time = createUnit(course, unitBuilder().completionType("TIME").timeLimitSeconds(30).build());

        assertAll(
                () -> assertEquals("QUESTION", question.getCompletionType()),
                () -> assertEquals("2+2?", question.getQuestion()),
                () -> assertEquals("4", question.getAnswer()),
                () -> assertEquals("TIME", time.getCompletionType()),
                () -> assertEquals(30, time.getTimeLimitSeconds())
        );
    }

    @Test
    @DisplayName("Юниты получают позиции 1, 2, 3 по порядку создания")
    void unitsGetSequentialPositions() {
        CourseResponse course = createCourse();

        UnitResponse first = createUnit(course, unitBuilder().build());
        UnitResponse second = createUnit(course, unitBuilder().build());
        UnitResponse third = createUnit(course, unitBuilder().build());

        assertAll(
                () -> assertEquals(1, first.getPosition()),
                () -> assertEquals(2, second.getPosition()),
                () -> assertEquals(3, third.getPosition())
        );
    }

    @Test
    @DisplayName("Граничные значения: name 80, timeLimitSeconds 1 и 86400 -> 201")
    void createUnitWithBoundaryValues() {
        CourseResponse course = createCourse();

        Response longName = UNIT_CONTROLLER.createUnitResponse(course.getId(), unitBuilder().name("n".repeat(80)).build());
        Response minTime = UNIT_CONTROLLER.createUnitResponse(course.getId(), unitBuilder().completionType("TIME").timeLimitSeconds(1).build());
        Response maxTime = UNIT_CONTROLLER.createUnitResponse(course.getId(), unitBuilder().completionType("TIME").timeLimitSeconds(86400).build());

        assertAll(
                () -> assertEquals(201, longName.statusCode(), longName.asString()),
                () -> assertEquals(201, minTime.statusCode(), minTime.asString()),
                () -> assertEquals(201, maxTime.statusCode(), maxTime.asString())
        );
    }

    // ---------- POST: negative ----------

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", unitBuilder().name(null).build()),
                Arguments.of("name пустой", unitBuilder().name("").build()),
                Arguments.of("name пробелы", unitBuilder().name("   ").build()),
                Arguments.of("name 81 символ", unitBuilder().name("n".repeat(81)).build()),
                Arguments.of("type = null", unitBuilder().type(null).build()),
                Arguments.of("CONTENT без textContent", unitBuilder().textContent(null).build()),
                Arguments.of("timeLimitSeconds = 0", unitBuilder().completionType("TIME").timeLimitSeconds(0).build()),
                Arguments.of("timeLimitSeconds = 86401", unitBuilder().completionType("TIME").timeLimitSeconds(86401).build()),
                Arguments.of("question 501 символ", unitBuilder().completionType("QUESTION").question("q".repeat(501)).answer("a").build()),
                Arguments.of("answer 201 символ", unitBuilder().completionType("QUESTION").question("q").answer("a".repeat(201)).build()),
                Arguments.of("YouTube без ссылки", UnitRequest.builder().name("v").type("VIDEO").sourceType("YOUTUBE").build()),
                Arguments.of("YouTube с неверной ссылкой", UnitRequest.builder().name("v").type("VIDEO").sourceType("YOUTUBE").youtubeUrl("notaurl").build()),
                Arguments.of("UPLOAD без файла", UnitRequest.builder().name("p").type("PRESENTATION").sourceType("UPLOAD").build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createUnitWithInvalidData(String caseName, UnitRequest request) {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createUnitResponse(course.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Файл недопустимого формата (.txt) -> 400")
    void createUnitWithDisallowedFileType() throws IOException {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createUnitResponse(course.getId(),
                UnitRequest.builder().name("Slides").type("PRESENTATION").sourceType("UPLOAD").build(), file("notes.txt"));

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "unit: {0}")
    // ПАДАЕТ (баг бэка) для пустой части unit: 500 вместо 400
    @ValueSource(strings = {"", "{}", "not a json", "[]", "{\"name\":\"x\",\"type\":\"BAD\"}", "{\"name\":\"x\",\"type\":\"CONTENT\",\"completionType\":\"BAD\"}"})
    @DisplayName("Некорректная часть unit -> 400")
    void createUnitWithMalformedUnitPart(String unitJson) {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createUnitRaw(course.getId(), unitJson, null);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Запрос без части unit -> 400")
    void createUnitWithoutUnitPart() throws IOException {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createUnitWithoutUnitPart(course.getId(), file("slides.pdf"));

        // ПАДАЕТ (баг бэка): 500 вместо 400 — отсутствие обязательной части не обрабатывается
        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Создание юнита в несуществующем курсе -> 404")
    void createUnitInUnknownCourse() {
        Response response = UNIT_CONTROLLER.createUnitResponse(UNKNOWN_ID, unitBuilder().build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Создание юнита с невалидным id курса -> 400")
    void createUnitWithInvalidCourseId() {
        Response response = UNIT_CONTROLLER.createUnitResponse("abc", unitBuilder().build());

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/courses/{id}/units и /api/units/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /courses/{id}/units -> 200, юниты в порядке позиций")
    void getUnitsOfCourse() {
        CourseResponse course = createCourse();
        UnitResponse first = createUnit(course, unitBuilder().build());
        UnitResponse second = createUnit(course, unitBuilder().build());

        Response response = UNIT_CONTROLLER.getUnitsResponse(course.getId());
        assertEquals(200, response.statusCode(), response.asString());

        UnitResponse[] units = response.as(UnitResponse[].class);
        assertAll(
                () -> assertEquals(2, units.length),
                () -> assertEquals(first.getId(), units[0].getId()),
                () -> assertEquals(second.getId(), units[1].getId())
        );
    }

    @Test
    @DisplayName("У нового курса нет юнитов -> пустой список")
    void newCourseHasNoUnits() {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.getUnitsResponse(course.getId());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("$").size())
        );
    }

    @Test
    @DisplayName("Юниты несуществующего курса -> 404, с невалидным id -> 400")
    void getUnitsOfUnknownCourse() {
        Response unknown = UNIT_CONTROLLER.getUnitsResponse(UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.getUnitsResponse("abc");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    @Tag("smoke")
    @Test
    @DisplayName("GET /units/{id} -> 200, данные совпадают, виден textContent")
    void getUnitById() {
        CourseResponse course = createCourse();
        UnitRequest request = unitBuilder().textContent("Содержимое урока").build();
        UnitResponse created = createUnit(course, request);

        Response response = UNIT_CONTROLLER.getUnitResponse(created.getId());
        assertEquals(200, response.statusCode(), response.asString());

        UnitResponse actual = response.as(UnitResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), actual.getId()),
                () -> assertEquals(request.getName(), actual.getName()),
                () -> assertEquals("Содержимое урока", actual.getTextContent())
        );
    }

    @Test
    @DisplayName("Несуществующий юнит -> 404, невалидный id -> 400")
    void getUnknownUnit() {
        Response unknown = UNIT_CONTROLLER.getUnitResponse(UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.getUnitResponse("abc");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- PUT /api/units/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление юнита -> 200, GET показывает новые значения, позиция не меняется")
    void updateUnit() {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());
        UnitRequest update = unitBuilder().textContent("Новый текст").active(false).build();

        Response response = UNIT_CONTROLLER.updateUnitResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        UnitResponse fromGet = UNIT_CONTROLLER.getUnitResponse(created.getId()).as(UnitResponse.class);
        assertAll(
                () -> assertEquals(update.getName(), response.as(UnitResponse.class).getName()),
                () -> assertEquals(update.getName(), fromGet.getName()),
                () -> assertEquals("Новый текст", fromGet.getTextContent()),
                () -> assertFalse(fromGet.isActive()),
                () -> assertEquals(created.getPosition(), fromGet.getPosition())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateInvalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateUnitWithInvalidData(String caseName, UnitRequest request) {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());

        Response response = UNIT_CONTROLLER.updateUnitResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    /** Для PUT нет загрузки файла, поэтому случай "UPLOAD без файла" не подходит */
    static Stream<Arguments> updateInvalidData() {
        return invalidData().filter(a -> !a.get()[0].toString().startsWith("UPLOAD"));
    }

    @Test
    @DisplayName("Обновление несуществующего юнита -> 404, невалидного id -> 400")
    void updateUnknownUnit() {
        Response unknown = UNIT_CONTROLLER.updateUnitResponse(UNKNOWN_ID, unitBuilder().build());
        Response invalid = UNIT_CONTROLLER.updateUnitRaw("abc", "{\"name\":\"x\",\"type\":\"CONTENT\"}");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "not a json", "[]", "{\"name\":\"x\",\"type\":\"BAD\"}"})
    @DisplayName("Обновление с некорректным телом -> 400")
    void updateUnitWithMalformedBody(String body) {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());

        Response response = UNIT_CONTROLLER.updateUnitRaw(created.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE /api/units/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление юнита -> 204, он пропадает из курса")
    void deleteUnit() {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());

        Response delete = UNIT_CONTROLLER.deleteUnitResponse(created.getId());

        UnitResponse[] units = UNIT_CONTROLLER.getUnitsResponse(course.getId()).as(UnitResponse[].class);
        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, UNIT_CONTROLLER.getUnitResponse(created.getId()).statusCode()),
                () -> assertTrue(Arrays.stream(units).noneMatch(u -> created.getId().equals(u.getId())))
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404, несуществующий -> 404, невалидный id -> 400")
    void deleteUnitNegative() {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());
        UNIT_CONTROLLER.deleteUnitResponse(created.getId());

        Response twice = UNIT_CONTROLLER.deleteUnitResponse(created.getId());
        Response unknown = UNIT_CONTROLLER.deleteUnitResponse(UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.deleteUnitResponse("abc");

        assertAll(
                () -> assertEquals(404, twice.statusCode(), twice.asString()),
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- POST /api/units/{id}/start, /complete ----------

    @Tag("smoke")
    @Test
    @DisplayName("start -> 200, проставляется startedAt; complete (CHECKBOX) -> 200, completed = true")
    void startAndCompleteCheckboxUnit() {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());

        Response start = UNIT_CONTROLLER.startResponse(created.getId());
        Response complete = UNIT_CONTROLLER.completeResponse(created.getId(), Map.of());

        assertAll(
                () -> assertEquals(200, start.statusCode(), start.asString()),
                () -> assertNotNull(start.as(UnitResponse.class).getStartedAt()),
                () -> assertEquals(200, complete.statusCode(), complete.asString()),
                () -> assertTrue(complete.as(UnitResponse.class).isCompleted()),
                () -> assertTrue(UNIT_CONTROLLER.getUnitResponse(created.getId()).as(UnitResponse.class).isCompleted())
        );
    }

    @Test
    @DisplayName("complete без start -> 400 'Open the unit first'")
    void completeWithoutStart() {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());

        Response response = UNIT_CONTROLLER.completeResponse(created.getId(), Map.of());

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("QUESTION: верный ответ -> 200 и completed, неверный или пустой -> 400")
    void completeQuestionUnit() {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().completionType("QUESTION").question("2+2?").answer("4").build());
        UNIT_CONTROLLER.startResponse(created.getId());

        Response wrong = UNIT_CONTROLLER.completeResponse(created.getId(), Map.of("answer", "5"));
        Response empty = UNIT_CONTROLLER.completeResponse(created.getId(), Map.of());
        Response right = UNIT_CONTROLLER.completeResponse(created.getId(), Map.of("answer", "4"));

        assertAll(
                () -> assertEquals(400, wrong.statusCode(), wrong.asString()),
                () -> assertEquals(400, empty.statusCode(), empty.asString()),
                () -> assertEquals(200, right.statusCode(), right.asString()),
                () -> assertTrue(right.as(UnitResponse.class).isCompleted())
        );
    }

    @Test
    @DisplayName("TIME: завершение раньше лимита -> 400 'Too early'")
    void completeTimeUnitTooEarly() {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().completionType("TIME").timeLimitSeconds(60).build());
        UNIT_CONTROLLER.startResponse(created.getId());

        Response response = UNIT_CONTROLLER.completeResponse(created.getId(), Map.of());

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("start и complete несуществующего юнита -> 404, невалидного id -> 400")
    void startAndCompleteNegative() {
        Response startUnknown = UNIT_CONTROLLER.startResponse(UNKNOWN_ID);
        Response completeUnknown = UNIT_CONTROLLER.completeResponse(UNKNOWN_ID, Map.of());
        Response startInvalid = UNIT_CONTROLLER.startResponse("abc");
        Response completeInvalid = UNIT_CONTROLLER.completeResponse("abc", Map.of());

        assertAll(
                () -> assertEquals(404, startUnknown.statusCode(), startUnknown.asString()),
                () -> assertEquals(404, completeUnknown.statusCode(), completeUnknown.asString()),
                () -> assertEquals(400, startInvalid.statusCode(), startInvalid.asString()),
                () -> assertEquals(400, completeInvalid.statusCode(), completeInvalid.asString())
        );
    }

    // ---------- POST /api/units/{id}/move ----------

    // тело {"direction": -1} двигает юнит вверх, {"direction": 1} вниз

    @Test
    @DisplayName("move вверх -> 204, юнит меняется местами с предыдущим")
    void moveUnitUp() {
        CourseResponse course = createCourse();
        UnitResponse first = createUnit(course, unitBuilder().build());
        UnitResponse second = createUnit(course, unitBuilder().build());

        Response move = UNIT_CONTROLLER.moveResponse(second.getId(), "{\"direction\":-1}");

        UnitResponse[] units = UNIT_CONTROLLER.getUnitsResponse(course.getId()).as(UnitResponse[].class);
        assertAll(
                () -> assertEquals(204, move.statusCode(), move.asString()),
                () -> assertEquals(second.getId(), units[0].getId()),
                () -> assertEquals(first.getId(), units[1].getId())
        );
    }

    @Test
    @DisplayName("move вниз -> 204, юнит меняется местами со следующим")
    void moveUnitDown() {
        CourseResponse course = createCourse();
        UnitResponse first = createUnit(course, unitBuilder().build());
        UnitResponse second = createUnit(course, unitBuilder().build());

        Response move = UNIT_CONTROLLER.moveResponse(first.getId(), "{\"direction\":1}");

        UnitResponse[] units = UNIT_CONTROLLER.getUnitsResponse(course.getId()).as(UnitResponse[].class);
        assertAll(
                () -> assertEquals(204, move.statusCode(), move.asString()),
                () -> assertEquals(second.getId(), units[0].getId()),
                () -> assertEquals(first.getId(), units[1].getId())
        );
    }

    @Test
    @DisplayName("move первого юнита вверх -> 204, порядок не меняется")
    void moveFirstUnitUpKeepsOrder() {
        CourseResponse course = createCourse();
        UnitResponse first = createUnit(course, unitBuilder().build());
        UnitResponse second = createUnit(course, unitBuilder().build());

        Response move = UNIT_CONTROLLER.moveResponse(first.getId(), "{\"direction\":-1}");

        UnitResponse[] units = UNIT_CONTROLLER.getUnitsResponse(course.getId()).as(UnitResponse[].class);
        assertAll(
                () -> assertEquals(204, move.statusCode(), move.asString()),
                () -> assertEquals(first.getId(), units[0].getId()),
                () -> assertEquals(second.getId(), units[1].getId())
        );
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"{}", "{\"direction\":99}", "{\"direction\":0}"})
    @DisplayName("move без направления или с недопустимым -> 400")
    void moveUnitWithInvalidDirection(String body) {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());

        // ПАДАЕТ (баг бэка): 204 вместо 400 — недопустимое направление принимается молча
        Response response = UNIT_CONTROLLER.moveResponse(created.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "not a json", "{\"direction\":\"up\"}"})
    @DisplayName("move с некорректным телом -> 400")
    void moveUnitWithMalformedBody(String body) {
        CourseResponse course = createCourse();
        UnitResponse created = createUnit(course, unitBuilder().build());

        Response response = UNIT_CONTROLLER.moveResponse(created.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("move несуществующего юнита -> 404, невалидного id -> 400")
    void moveUnknownUnit() {
        Response unknown = UNIT_CONTROLLER.moveResponse(UNKNOWN_ID, "{\"direction\":-1}");
        Response invalid = UNIT_CONTROLLER.moveResponse("abc", "{\"direction\":-1}");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- GET /api/units/files/{userId} ----------

    @Test
    @DisplayName("Файлы пользователя без файлов -> 200 и пустой список, невалидный id -> 400")
    void userFiles() {
        Response empty = UNIT_CONTROLLER.userFilesResponse(UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.userFilesResponse("abc");

        assertAll(
                () -> assertEquals(200, empty.statusCode(), empty.asString()),
                () -> assertEquals(0, empty.jsonPath().getList("$").size()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }
}
