import io.restassured.response.Response;
import kg.jazzlms.config.ConfigReader;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.GamificationController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.EnrollmentResponse;
import kg.jazzlms.models.response.UserGamificationResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import kg.jazzlms.utils.Waiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Очки начисляются по событиям из других сервисов (через Kafka),
 * поэтому после действия ждём изменения профиля (до 15 секунд).
 */
@Tag("regression")
@DisplayName("Gamification service: профиль пользователя (/me, /users/{id}, начисление очков)")
public class GamificationUserTest {

    private static final GamificationController GAMIFICATION = new GamificationController(Endpoints.GAMIFICATION_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final AuthController AUTH_CONTROLLER = new AuthController(Endpoints.BASE_URL);
    private static final CourseController COURSE_CONTROLLER = new CourseController(Endpoints.COURSE_URL);
    private static final List<UUID> USERS = new ArrayList<>();
    private static final List<UUID> COURSES = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final int WAIT_SECONDS = 15;

    private UserRequest request;

    private UserResponse createLoginableUser() {
        request = EntityManager.loginableUserBuilder().build();
        UserResponse created = USER_CONTROLLER.createUser(request);
        USERS.add(created.getId());
        return created;
    }

    private String adminId() {
        return AUTH_CONTROLLER.login(ConfigReader.getValue("admin.username"), ConfigReader.getValue("admin.password"))
                .getUser().getId().toString();
    }

    @AfterAll
    static void cleanUp() {
        COURSES.forEach(id -> {
            COURSE_CONTROLLER.deleteCourseResponse(id);
            COURSE_CONTROLLER.deleteCoursePermanentlyResponse(id);
        });
        USERS.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    // ---------- GET /api/gamification/me ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /me -> 200, профиль администратора с уровнем, бейджами и историей")
    void getMe() {
        Response response = GAMIFICATION.meResponse();
        assertEquals(200, response.statusCode(), response.asString());

        UserGamificationResponse me = response.as(UserGamificationResponse.class);
        assertAll(
                () -> assertEquals(adminId(), me.getUserId()),
                () -> assertTrue(me.getPoints() >= 0),
                () -> assertTrue(me.getLevel() >= 1),
                () -> assertTrue(me.getNextLevelPoints() > me.getPoints(), "До следующего уровня должно быть больше очков, чем сейчас"),
                () -> assertNotNull(me.getBadges()),
                () -> assertNotNull(me.getHistory())
        );
    }

    @Test
    @DisplayName("У бейджа есть code, name, category и дата получения")
    void badgeHasRequiredFields() {
        UserGamificationResponse me = GAMIFICATION.meResponse().as(UserGamificationResponse.class);

        assertAll(
                () -> assertFalse(me.getBadges().isEmpty(), "У администратора должен быть хотя бы один бейдж"),
                () -> assertTrue(me.getBadges().stream().allMatch(b -> b.get("code") != null && b.get("name") != null
                        && b.get("category") != null && b.get("awardedAt") != null))
        );
    }

    // ---------- GET /api/gamification/users/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /users/{id} администратора совпадает с /me")
    void getUserById() {
        UserGamificationResponse me = GAMIFICATION.meResponse().as(UserGamificationResponse.class);

        Response response = GAMIFICATION.userResponse(me.getUserId());
        assertEquals(200, response.statusCode(), response.asString());

        UserGamificationResponse byId = response.as(UserGamificationResponse.class);
        assertAll(
                () -> assertEquals(me.getUserId(), byId.getUserId()),
                () -> assertEquals(me.getLevel(), byId.getLevel()),
                () -> assertEquals(me.getBadges().size(), byId.getBadges().size())
        );
    }

    @Test
    @DisplayName("Пользователь без активности -> 200, 0 очков, уровень 1, пустые бейджи и история")
    void userWithoutActivity() {
        Response response = GAMIFICATION.userResponse(UNKNOWN_ID);
        assertEquals(200, response.statusCode(), response.asString());

        UserGamificationResponse profile = response.as(UserGamificationResponse.class);
        assertAll(
                () -> assertEquals(0, profile.getPoints()),
                () -> assertEquals(1, profile.getLevel()),
                () -> assertTrue(profile.getBadges().isEmpty()),
                () -> assertTrue(profile.getHistory().isEmpty()),
                () -> assertNull(profile.getRank())
        );
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный id -> 400")
    void getUserWithInvalidId(String id) {
        // ПАДАЕТ (баг бэка): 500 вместо 400 — неверный формат UUID не обрабатывается
        Response response = GAMIFICATION.userResponse(id);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- начисление очков по событиям ----------

    @Tag("smoke")
    @Test
    @DisplayName("Первый вход нового пользователя -> +10 очков, 1 вход, уровень 1, запись в истории")
    void firstLoginGivesPoints() {
        UserResponse user = createLoginableUser();
        AUTH_CONTROLLER.login(request.getUsername(), request.getPassword());

        assertTrue(Waiter.waitUntil(WAIT_SECONDS, () -> GAMIFICATION.user(user.getId()).getPoints() >= 10),
                "Очки за вход не начислились за " + WAIT_SECONDS + " секунд");

        UserGamificationResponse profile = GAMIFICATION.user(user.getId());
        assertAll(
                () -> assertEquals(10, profile.getPoints()),
                () -> assertEquals(1, profile.getLogins()),
                () -> assertEquals(1, profile.getLevel()),
                () -> assertEquals(300, profile.getNextLevelPoints()),
                () -> assertTrue(profile.getHistory().stream().anyMatch(h -> Integer.valueOf(10).equals(h.get("points")))),
                () -> assertTrue(profile.getBadges().stream().anyMatch(b -> "newcomer".equals(b.get("code"))), "Бейдж Newcomer не выдан")
        );
    }

    @Test
    @DisplayName("Повторный вход в тот же день очков не добавляет (once per day)")
    void secondLoginSameDayGivesNoPoints() {
        UserResponse user = createLoginableUser();
        AUTH_CONTROLLER.login(request.getUsername(), request.getPassword());
        assertTrue(Waiter.waitUntil(WAIT_SECONDS, () -> GAMIFICATION.user(user.getId()).getPoints() >= 10));

        AUTH_CONTROLLER.login(request.getUsername(), request.getPassword());
        AUTH_CONTROLLER.login(request.getUsername(), request.getPassword());
        // лишние события успеют дойти до сервиса; ждём заведомо дольше, чем в других тестах
        Waiter.waitUntil(5, () -> false);

        assertEquals(10, GAMIFICATION.user(user.getId()).getPoints());
    }

    @Test
    @DisplayName("Завершение курса -> очки за курс (не меньше 100), счётчик курсов растёт")
    void courseCompletionGivesPoints() {
        UserResponse user = createLoginableUser();
        CourseResponse course = COURSE_CONTROLLER.createCourse(EntityManager.generateCourse());
        COURSES.add(course.getId());
        EnrollmentResponse enrollment = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "LEARNER")
                .then().statusCode(201).extract().as(EnrollmentResponse.class);

        COURSE_CONTROLLER.updateProgressResponse(enrollment.getId(), 100);

        assertTrue(Waiter.waitUntil(WAIT_SECONDS, () -> GAMIFICATION.user(user.getId()).getPoints() >= 100),
                "Очки за курс не начислились за " + WAIT_SECONDS + " секунд");
        UserGamificationResponse profile = GAMIFICATION.user(user.getId());
        assertAll(
                () -> assertEquals(1, profile.getCourses()),
                () -> assertTrue(profile.getHistory().stream().anyMatch(h -> Integer.valueOf(100).equals(h.get("points"))),
                        "В истории нет начисления 100 очков")
        );
    }

    // ---------- POST /api/gamification/reset-statistics ----------

    @Disabled("Обнуляет очки и бейджи ВСЕХ пользователей во всём окружении — запускать вручную и только на тестовом стенде")
    @Test
    @DisplayName("POST /reset-statistics -> 204, у пользователей 0 очков")
    void resetStatistics() {
        Response response = GAMIFICATION.resetStatisticsResponse();

        assertAll(
                () -> assertEquals(204, response.statusCode(), response.asString()),
                () -> assertEquals(0, GAMIFICATION.meResponse().as(UserGamificationResponse.class).getPoints())
        );
    }
}
