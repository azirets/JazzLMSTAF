import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.CertificateController;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.GamificationController;
import kg.jazzlms.controller.QuestionController;
import kg.jazzlms.controller.UnitController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.request.TestRequest;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.AttemptResponse;
import kg.jazzlms.models.response.CertificateResponse;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.MyEnrollmentResponse;
import kg.jazzlms.models.response.QuestionResponse;
import kg.jazzlms.models.response.TestResponse;
import kg.jazzlms.models.response.UnitResponse;
import kg.jazzlms.models.response.UserGamificationResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import kg.jazzlms.utils.Waiter;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static kg.jazzlms.utils.EntityManager.questionBuilder;
import static kg.jazzlms.utils.EntityManager.testBuilder;
import static kg.jazzlms.utils.EntityManager.unitBuilder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: геймификация на полном пути ученика.
 *
 * Сценарий: админ создаёт ученика и курс (2 урока + тест) и записывает ученика ->
 * ученик входит, проходит уроки, проваливает и сдаёт тест -> курс завершается, выдаётся сертификат.
 * После каждого шага проверяем, что очки, счётчики, уровень, бейджи и история начислений в
 * gamification-service совпадают с настройками (сколько очков за вход, урок, тест, курс и сертификат).
 *
 * Ученик действует через API gateway (8080), как фронт: напрямую сервисы пускают только админа.
 * События доходят до gamification-service через Kafka, поэтому после каждого действия ждём
 * (до 15 секунд), а не проверяем мгновенно.
 *
 * Шаги зависят друг от друга, поэтому идут по @Order в одном экземпляре класса.
 * Всё созданное удаляется в @AfterAll.
 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E: gamification — очки за вход, уроки, тест, курс и сертификат")
public class GamificationE2ETest {

    private static final int WAIT_SECONDS = 15;

    private final UserController adminUsers = new UserController(Endpoints.BASE_URL);
    private final AuthController auth = new AuthController(Endpoints.BASE_URL);
    private final CourseController adminCourses = new CourseController(Endpoints.COURSE_URL);
    private final UnitController adminUnits = new UnitController(Endpoints.COURSE_URL);
    private final QuestionController adminQuestions = new QuestionController(Endpoints.COURSE_URL);
    private final CertificateController adminCertificates = new CertificateController(Endpoints.COURSE_URL);
    private final GamificationController gamification = new GamificationController(Endpoints.GAMIFICATION_URL);

    // настройки начисления на момент старта: от них считаем ожидаемые очки
    private int loginPoints, unitPoints, testPoints, coursePoints, certificatePoints, levelStep;

    private UserRequest learnerReq;
    private UserResponse learner;
    private String learnerToken;
    private UnitController learnerUnits;
    private CourseController learnerCourses;

    private CourseResponse course;
    private UnitResponse lesson1, lesson2;
    private TestResponse test;
    private QuestionResponse question;

    @BeforeAll
    void setUp() {
        GamificationSettings settings = gamification.getSettings();
        assertTrue(settings.getEnabled() && settings.getPoints().getEnabled(),
                "Для сценария геймификация и начисление очков должны быть включены в настройках");
        loginPoints = settings.getPoints().getLogin().getPoints();
        unitPoints = settings.getPoints().getUnit().getPoints();
        testPoints = settings.getPoints().getTest().getPoints();
        coursePoints = settings.getPoints().getCourse().getPoints();
        certificatePoints = settings.getPoints().getCertificate().getPoints();
        levelStep = settings.getLevels().getByPoints().getEvery();
    }

    // ---------------------------------------------------------------- подготовка

    @Test
    @Order(1)
    @DisplayName("1. Админ создаёт ученика: 0 очков, уровень 1, нет бейджей и истории")
    void newLearnerStartsFromZero() {
        learnerReq = EntityManager.loginableUserBuilder().firstName("Aibek").lastName("Gamer").build();
        learner = adminUsers.createUser(learnerReq);

        UserGamificationResponse profile = gamification.user(learner.getId());
        assertAll(
                () -> assertEquals(0, profile.getPoints()),
                () -> assertEquals(1, profile.getLevel()),
                () -> assertEquals(levelStep, profile.getNextLevelPoints()),
                () -> assertEquals(0, profile.getLogins()),
                () -> assertEquals(0, profile.getUnits()),
                () -> assertEquals(0, profile.getCourses()),
                () -> assertTrue(profile.getBadges().isEmpty()),
                () -> assertTrue(profile.getHistory().isEmpty())
        );
    }

    @Test
    @Order(2)
    @DisplayName("2. Админ создаёт курс: 2 урока и тест, записывает ученика; очков по-прежнему 0")
    void adminCreatesCourseAndEnrollsLearner() {
        course = adminCourses.createCourse(EntityManager.generateCourse());
        lesson1 = adminUnits.createUnit(course.getId(), unitBuilder().name("Lesson 1").build());
        lesson2 = adminUnits.createUnit(course.getId(), unitBuilder().name("Lesson 2").build());
        question = adminQuestions.createQuestion(course.getId(), questionBuilder().build());
        // правильный ответ в questionBuilder() — первый вариант, его id = 0
        test = adminUnits.createTest(course.getId(), testBuilder().name("Final test").passScore(50).repetitions("ALWAYS")
                .questions(List.of(new TestRequest.Item(question.getId(), 1))).build());

        Response enroll = adminCourses.enrollResponse(course.getId(), learner.getId(), "LEARNER");
        assertEquals(201, enroll.statusCode(), enroll.asString());

        assertEquals(0, gamification.user(learner.getId()).getPoints(), "Запись на курс не должна давать очков");
    }

    // ---------------------------------------------------------------- вход

    @Test
    @Order(3)
    @DisplayName("3. Первый вход: очки за вход, 1 вход, бейдж Newcomer, запись в истории")
    void firstLoginGivesPoints() {
        learnerToken = auth.login(learnerReq.getUsername(), learnerReq.getPassword()).getToken();
        learnerUnits = new UnitController(Endpoints.GATEWAY_URL, learnerToken);
        learnerCourses = new CourseController(Endpoints.GATEWAY_URL, learnerToken);

        UserGamificationResponse profile = awaitPoints(loginPoints);
        assertAll(
                () -> assertEquals(1, profile.getLogins()),
                () -> assertEquals(1, profile.getLevel()),
                () -> assertTrue(hasBadge(profile, "newcomer"), "Бейдж Newcomer не выдан"),
                () -> assertEquals(1, historyEntries(profile, "Sign in").size()),
                () -> assertEquals(loginPoints, historyPoints(profile, "Sign in"))
        );
    }

    @Test
    @Order(4)
    @DisplayName("4. Повторный вход в тот же день очков не добавляет (once per day)")
    void secondLoginSameDayGivesNothing() {
        auth.login(learnerReq.getUsername(), learnerReq.getPassword());
        auth.login(learnerReq.getUsername(), learnerReq.getPassword());
        Waiter.waitUntil(4, () -> false); // даём событиям дойти до сервиса

        UserGamificationResponse profile = gamification.user(learner.getId());
        assertAll(
                () -> assertEquals(loginPoints, profile.getPoints()),
                () -> assertEquals(1, historyEntries(profile, "Sign in").size())
        );
    }

    // ---------------------------------------------------------------- уроки

    @Test
    @Order(5)
    @DisplayName("5. Урок 1: очки за урок, 1 юнит, бейдж First step, прогресс курса ~33%")
    void firstLessonGivesPoints() {
        completeLesson(lesson1);

        UserGamificationResponse profile = awaitPoints(loginPoints + unitPoints);
        int progress = progressOfCourse();
        assertAll(
                () -> assertEquals(1, profile.getUnits()),
                () -> assertTrue(hasBadge(profile, "first-step"), "Бейдж First step не выдан"),
                () -> assertEquals(1, historyEntries(profile, "Unit completed").size()),
                () -> assertTrue(progress >= 33 && progress <= 34, "Прогресс после 1 из 3 юнитов: " + progress)
        );
    }

    @Test
    @Order(6)
    @DisplayName("6. Урок 2: ещё одни очки за урок, 2 юнита, прогресс курса ~66%")
    void secondLessonGivesPoints() {
        completeLesson(lesson2);

        UserGamificationResponse profile = awaitPoints(loginPoints + 2 * unitPoints);
        int progress = progressOfCourse();
        assertAll(
                () -> assertEquals(2, profile.getUnits()),
                () -> assertEquals(2, historyEntries(profile, "Unit completed").size()),
                () -> assertTrue(progress >= 66 && progress <= 67, "Прогресс после 2 из 3 юнитов: " + progress),
                () -> assertEquals(0, profile.getCourses(), "Курс ещё не завершён")
        );
    }

    // ---------------------------------------------------------------- тест

    @Test
    @Order(7)
    @DisplayName("7. Проваленный тест: очков не прибавляется, курс не завершён")
    void failedTestGivesNothing() {
        assertEquals(200, learnerUnits.startResponse(test.getUnitId()).statusCode());
        AttemptResponse attempt = learnerUnits.startAttempt(test.getUnitId());

        Response submit = learnerUnits.submitAttemptResponse(test.getUnitId(), attempt.getId(),
                Map.of("answers", Map.of(question.getId().toString(), List.of(1))));
        assertEquals(200, submit.statusCode(), submit.asString());
        AttemptResponse result = submit.as(AttemptResponse.class);
        assertAll(
                () -> assertFalse(result.getPassed()),
                () -> assertEquals(0, result.getScore())
        );

        Waiter.waitUntil(4, () -> false); // даём событиям дойти до сервиса
        UserGamificationResponse profile = gamification.user(learner.getId());
        assertAll(
                () -> assertEquals(loginPoints + 2 * unitPoints, profile.getPoints()),
                () -> assertEquals(0, profile.getCourses()),
                () -> assertTrue(historyEntries(profile, "Test passed").isEmpty())
        );
    }

    @Test
    @Order(8)
    @DisplayName("8. Сданный тест: очки за тест и за юнит, курс завершён, сертификат выдан, очки за курс и сертификат")
    void passedTestCompletesCourse() {
        AttemptResponse attempt = learnerUnits.startAttempt(test.getUnitId());

        Response submit = learnerUnits.submitAttemptResponse(test.getUnitId(), attempt.getId(),
                Map.of("answers", Map.of(question.getId().toString(), List.of(0))));
        assertEquals(200, submit.statusCode(), submit.asString());
        AttemptResponse result = submit.as(AttemptResponse.class);
        assertAll(
                () -> assertTrue(result.getPassed()),
                () -> assertEquals(100, result.getScore())
        );

        int expected = loginPoints + 3 * unitPoints + testPoints + coursePoints + certificatePoints;
        UserGamificationResponse profile = awaitPoints(expected);

        MyEnrollmentResponse enrollment = findMyEnrollment();
        List<CertificateResponse> certificates = List.of(adminCertificates.userCertificatesResponse(learner.getId()).as(CertificateResponse[].class));
        assertAll(
                () -> assertEquals(100, enrollment.getProgress()),
                () -> assertNotNull(enrollment.getCompletedAt(), "Курс не помечен завершённым"),
                () -> assertEquals(1, certificates.size(), "Сертификат не выдан"),
                () -> assertEquals(course.getId(), certificates.get(0).getCourseId()),
                () -> assertEquals(3, profile.getUnits()),
                () -> assertEquals(1, profile.getCourses())
        );
    }

    // ---------------------------------------------------------------- итог

    @Test
    @Order(9)
    @DisplayName("9. История: каждое начисление ровно один раз, сумма истории равна очкам профиля")
    void historyMatchesTotal() {
        UserGamificationResponse profile = gamification.user(learner.getId());

        int historySum = profile.getHistory().stream().mapToInt(h -> ((Number) h.get("points")).intValue()).sum();
        assertAll(
                () -> assertEquals(1, historyEntries(profile, "Sign in").size()),
                () -> assertEquals(loginPoints, historyPoints(profile, "Sign in")),
                () -> assertEquals(3, historyEntries(profile, "Unit completed").size()),
                () -> assertEquals(3 * unitPoints, historyPoints(profile, "Unit completed")),
                () -> assertEquals(1, historyEntries(profile, "Test passed").size()),
                () -> assertEquals(testPoints, historyPoints(profile, "Test passed")),
                () -> assertEquals(1, historyEntries(profile, "Course completed").size()),
                () -> assertEquals(coursePoints, historyPoints(profile, "Course completed")),
                () -> assertEquals(1, historyEntries(profile, "Certificate").size()),
                () -> assertEquals(certificatePoints, historyPoints(profile, "Certificate")),
                () -> assertEquals(profile.getPoints(), historySum, "Сумма начислений в истории не равна очкам профиля")
        );
    }

    @Test
    @Order(10)
    @DisplayName("10. Итог: очки, счётчики, бейджи (Newcomer, First step, Graduate), уровень и место в рейтинге")
    void finalProfile() {
        int expected = loginPoints + 3 * unitPoints + testPoints + coursePoints + certificatePoints;

        UserGamificationResponse profile = gamification.user(learner.getId());
        assertAll(
                () -> assertEquals(expected, profile.getPoints()),
                () -> assertEquals(1, profile.getLogins()),
                () -> assertEquals(3, profile.getUnits()),
                () -> assertEquals(1, profile.getCourses()),
                () -> assertTrue(hasBadge(profile, "newcomer")),
                () -> assertTrue(hasBadge(profile, "first-step")),
                () -> assertTrue(hasBadge(profile, "graduate"), "Бейдж Graduate за курс не выдан"),
                () -> assertEquals(1 + expected / levelStep, profile.getLevel(), "Уровень по очкам: 1 + очки / шаг уровня"),
                () -> assertEquals(levelStep * (profile.getLevel()), profile.getNextLevelPoints(), "Очки до следующего уровня"),
                () -> assertNotNull(profile.getRank(), "Нет места в рейтинге по очкам"),
                () -> assertTrue(profile.getRank() >= 1)
        );
    }

    // ---------------------------------------------------------------- cleanup

    @AfterAll
    void cleanUp() {
        if (course != null) {
            adminCourses.deleteCourseResponse(course.getId());
            adminCourses.deleteCoursePermanentlyResponse(course.getId());
        }
        if (learner != null) {
            adminUsers.deleteUserResponse(learner.getId());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Ученик открывает юнит и завершает его (тип CHECKBOX — достаточно отметки) */
    private void completeLesson(UnitResponse lesson) {
        Response start = learnerUnits.startResponse(lesson.getId());
        assertEquals(200, start.statusCode(), start.asString());
        Response complete = learnerUnits.completeResponse(lesson.getId(), Map.of());
        assertEquals(200, complete.statusCode(), complete.asString());
    }

    /** Ждёт, пока очки пользователя станут ровно expected (события идут через Kafka), и возвращает профиль */
    private UserGamificationResponse awaitPoints(int expected) {
        Waiter.waitUntil(WAIT_SECONDS, () -> gamification.user(learner.getId()).getPoints() == expected);
        UserGamificationResponse profile = gamification.user(learner.getId());
        assertEquals(expected, profile.getPoints(), "Очки не стали ожидаемыми за " + WAIT_SECONDS + " секунд. История: " + profile.getHistory());
        return profile;
    }

    private MyEnrollmentResponse findMyEnrollment() {
        return learnerCourses.getMyEnrollments().stream()
                .filter(e -> e.getCourse() != null && course.getId().equals(e.getCourse().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Курса нет в 'Моих курсах' ученика"));
    }

    private int progressOfCourse() {
        return findMyEnrollment().getProgress();
    }

    private static boolean hasBadge(UserGamificationResponse profile, String code) {
        return profile.getBadges().stream().anyMatch(b -> code.equals(b.get("code")));
    }

    private static List<Map<String, Object>> historyEntries(UserGamificationResponse profile, String action) {
        return profile.getHistory().stream().filter(h -> action.equals(h.get("action"))).toList();
    }

    private static int historyPoints(UserGamificationResponse profile, String action) {
        return historyEntries(profile, action).stream().mapToInt(h -> ((Number) h.get("points")).intValue()).sum();
    }
}
