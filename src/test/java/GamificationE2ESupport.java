import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.GamificationController;
import kg.jazzlms.controller.QuestionController;
import kg.jazzlms.controller.UnitController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.request.TestRequest;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.AttemptResponse;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.QuestionResponse;
import kg.jazzlms.models.response.TestResponse;
import kg.jazzlms.models.response.UnitResponse;
import kg.jazzlms.models.response.UserGamificationResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import kg.jazzlms.utils.Waiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static kg.jazzlms.utils.EntityManager.questionBuilder;
import static kg.jazzlms.utils.EntityManager.testBuilder;
import static kg.jazzlms.utils.EntityManager.unitBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Общая часть E2E-тестов геймификации с разными настройками (выключено всё / очки / правила / бейджи / уровни).
 *
 * Каждый тест: меняет настройки -> {@link #play} проводит нового ученика по полному пути
 * (вход, урок, тест, завершение курса, сертификат) -> сравнивает профиль в gamification-service
 * с тем, что должны давать настройки.
 *
 * Настройки геймификации общие на всё окружение, поэтому перед каждым тестом они запоминаются,
 * а после — возвращаются как были. Тесты нельзя запускать параллельно.
 *
 * Ученик действует через API gateway (8080): напрямую сервисы пускают только админа.
 * События идут в gamification-service через Kafka, поэтому после действий ждём (до 15 секунд).
 */
public abstract class GamificationE2ESupport {

    protected static final int WAIT_SECONDS = 15;
    /** Сколько ждать, когда событий в сервисе быть не должно: ждать нечего, просто даём им шанс дойти */
    protected static final int QUIET_SECONDS = 6;

    protected static final UserController ADMIN_USERS = new UserController(Endpoints.BASE_URL);
    protected static final AuthController AUTH = new AuthController(Endpoints.BASE_URL);
    protected static final CourseController ADMIN_COURSES = new CourseController(Endpoints.COURSE_URL);
    protected static final UnitController ADMIN_UNITS = new UnitController(Endpoints.COURSE_URL);
    protected static final QuestionController ADMIN_QUESTIONS = new QuestionController(Endpoints.COURSE_URL);
    protected static final GamificationController GAMIFICATION = new GamificationController(Endpoints.GAMIFICATION_URL);

    private static final List<UUID> USERS = new ArrayList<>();
    private static final List<UUID> COURSES = new ArrayList<>();

    /** Настройки, которые были до теста */
    protected GamificationSettings original;

    @BeforeEach
    void rememberSettings() {
        original = GAMIFICATION.getSettings();
    }

    @AfterEach
    void restoreSettings() {
        Response restored = GAMIFICATION.updateSettingsResponse(original);
        assertEquals(200, restored.statusCode(), "Не удалось вернуть настройки геймификации как были: " + restored.asString());
    }

    @AfterAll
    static void cleanUp() {
        COURSES.forEach(id -> {
            ADMIN_COURSES.deleteCourseResponse(id);
            ADMIN_COURSES.deleteCoursePermanentlyResponse(id);
        });
        USERS.forEach(ADMIN_USERS::deleteUserResponse);
        COURSES.clear();
        USERS.clear();
    }

    // ---------------------------------------------------------------- сценарий ученика

    /** Ученик после прохождения: его id, токен и профиль геймификации */
    protected static class Run {
        final UUID userId;
        final UserRequest request;
        final UnitController learnerUnits;
        UserGamificationResponse profile;

        Run(UUID userId, UserRequest request, UnitController learnerUnits) {
            this.userId = userId;
            this.request = request;
            this.learnerUnits = learnerUnits;
        }
    }

    /**
     * Полный путь нового ученика: вход -> урок -> тест (верный ответ) -> курс завершён -> сертификат.
     * Это 1 вход, 2 юнита (урок и тест), 1 тест, 1 курс и 1 сертификат.
     *
     * @param expectEvents false, если события в сервисе учитываться не должны (геймификация выключена):
     *                     тогда ждём фиксированное время, а не конкретные записи в истории
     */
    protected Run play(boolean expectEvents) {
        UserRequest request = EntityManager.loginableUserBuilder().firstName("Aibek").lastName("Gamer").build();
        UserResponse user = ADMIN_USERS.createUser(request);
        USERS.add(user.getId());

        CourseResponse course = ADMIN_COURSES.createCourse(EntityManager.generateCourse());
        COURSES.add(course.getId());
        UnitResponse lesson = ADMIN_UNITS.createUnit(course.getId(), unitBuilder().name("Lesson").build());
        QuestionResponse question = ADMIN_QUESTIONS.createQuestion(course.getId(), questionBuilder().build());
        // правильный ответ в questionBuilder() — первый вариант, его id = 0
        TestResponse test = ADMIN_UNITS.createTest(course.getId(), testBuilder().name("Final test").passScore(50)
                .repetitions("ALWAYS").questions(List.of(new TestRequest.Item(question.getId(), 1))).build());
        Response enroll = ADMIN_COURSES.enrollResponse(course.getId(), user.getId(), "LEARNER");
        assertEquals(201, enroll.statusCode(), enroll.asString());

        String token = AUTH.login(request.getUsername(), request.getPassword()).getToken();
        UnitController learnerUnits = new UnitController(Endpoints.GATEWAY_URL, token);

        completeUnit(learnerUnits, lesson.getId());
        assertEquals(200, learnerUnits.startResponse(test.getUnitId()).statusCode());
        AttemptResponse attempt = learnerUnits.startAttempt(test.getUnitId());
        Response submit = learnerUnits.submitAttemptResponse(test.getUnitId(), attempt.getId(),
                Map.of("answers", Map.of(question.getId().toString(), List.of(0))));
        assertEquals(200, submit.statusCode(), submit.asString());
        assertTrue(submit.as(AttemptResponse.class).getPassed(), "Тест должен быть сдан: " + submit.asString());

        Run run = new Run(user.getId(), request, learnerUnits);
        if (expectEvents) {
            // последние события сценария: сдан тест и выдан сертификат (даже при 0 очков запись в истории появляется)
            boolean done = Waiter.waitUntil(WAIT_SECONDS, () -> {
                UserGamificationResponse p = GAMIFICATION.user(user.getId());
                return !historyEntries(p, "Test passed").isEmpty()
                        && !historyEntries(p, "Certificate").isEmpty()
                        && historyEntries(p, "Unit completed").size() == 2;
            });
            assertTrue(done, "События сценария не дошли до gamification-service за " + WAIT_SECONDS + " секунд. История: "
                    + GAMIFICATION.user(user.getId()).getHistory());
            Waiter.waitUntil(1, () -> false); // бейджи пишутся рядом с этими событиями — даём им дойти
        } else {
            Waiter.waitUntil(QUIET_SECONDS, () -> false);
        }
        run.profile = GAMIFICATION.user(user.getId());
        return run;
    }

    /** Ещё один курс с одним уроком, на который записывают того же ученика и который он проходит */
    protected void completeExtraLesson(Run run) {
        CourseResponse course = ADMIN_COURSES.createCourse(EntityManager.generateCourse());
        COURSES.add(course.getId());
        UnitResponse lesson = ADMIN_UNITS.createUnit(course.getId(), unitBuilder().name("Extra lesson").build());
        Response enroll = ADMIN_COURSES.enrollResponse(course.getId(), run.userId, "LEARNER");
        assertEquals(201, enroll.statusCode(), enroll.asString());
        completeUnit(run.learnerUnits, lesson.getId());
    }

    private static void completeUnit(UnitController learnerUnits, UUID unitId) {
        Response start = learnerUnits.startResponse(unitId);
        assertEquals(200, start.statusCode(), start.asString());
        Response complete = learnerUnits.completeResponse(unitId, Map.of());
        assertEquals(200, complete.statusCode(), complete.asString());
    }

    // ---------------------------------------------------------------- расчёт ожидаемого

    /**
     * Сколько очков должен набрать {@link #play} при данных настройках:
     * вход x1, юниты x2 (урок и тест), тест x1, курс x1, сертификат x1.
     */
    protected static int expectedPoints(GamificationSettings s) {
        if (!s.getEnabled() || !s.getPoints().getEnabled()) {
            return 0;
        }
        GamificationSettings.Points p = s.getPoints();
        return rule(p.getLogin()) + 2 * rule(p.getUnit()) + rule(p.getTest()) + rule(p.getCourse()) + rule(p.getCertificate());
    }

    private static int rule(GamificationSettings.PointRule rule) {
        return rule.getEnabled() ? rule.getPoints() : 0;
    }

    /** Копия настроек, где заменены только очки (остальное как было) */
    protected GamificationSettings withPoints(GamificationSettings.Points points) {
        return original.toBuilder().points(points).build();
    }

    protected void apply(GamificationSettings settings) {
        Response response = GAMIFICATION.updateSettingsResponse(settings);
        assertEquals(200, response.statusCode(), "Не удалось применить настройки: " + response.asString());
    }

    // ---------------------------------------------------------------- разбор профиля

    protected static List<Map<String, Object>> historyEntries(UserGamificationResponse profile, String action) {
        return profile.getHistory().stream().filter(h -> action.equals(h.get("action"))).toList();
    }

    protected static int historyPoints(UserGamificationResponse profile, String action) {
        return historyEntries(profile, action).stream().mapToInt(h -> ((Number) h.get("points")).intValue()).sum();
    }

    protected static List<String> badgeCodes(UserGamificationResponse profile) {
        return profile.getBadges().stream().map(b -> (String) b.get("code")).toList();
    }

    /** Записи истории о выдаче бейджей ("Badge: ...") */
    protected static List<Map<String, Object>> badgeHistory(UserGamificationResponse profile) {
        return profile.getHistory().stream().filter(h -> ((String) h.get("action")).startsWith("Badge")).toList();
    }
}
