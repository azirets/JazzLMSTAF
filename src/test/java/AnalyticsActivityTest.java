import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AnalyticsController;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import kg.jazzlms.utils.Waiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Analytics service: активность пользователя и курса")
public class AnalyticsActivityTest {

    private final AnalyticsController analytics = new AnalyticsController(Endpoints.ANALYTICS_URL);
    private final UserController userController = new UserController(Endpoints.BASE_URL);
    private final AuthController authController = new AuthController(Endpoints.BASE_URL);
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /** Для курса поля loginsLastWeek/loginsLastMonth в ответе нет, они только у пользователя */
    private static void assertSeriesAreConsistent(Response response) {
        int labels = response.jsonPath().getList("labels").size();
        assertAll(
                () -> assertTrue(labels > 0, "Нет отсчётов"),
                () -> assertEquals(labels, response.jsonPath().getList("logins").size()),
                () -> assertEquals(labels, response.jsonPath().getList("completions").size())
        );
    }

    // ---------- GET /api/analytics/users/{id}/activity ----------

    @Tag("smoke")
    @Test
    @DisplayName("Активность пользователя без period -> 200, период month, ряды одной длины")
    void userActivityDefaults() {
        UserResponse admin = userController.getAllUsers().get(0);

        Response response = analytics.userActivityResponse(admin.getId());
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals("month", response.jsonPath().getString("period")),
                () -> assertSeriesAreConsistent(response)
        );
    }

    @Test
    @DisplayName("У пользователя есть счётчики входов за неделю и месяц, за месяц не меньше")
    void userActivityHasLoginCounters() {
        UserResponse user = userController.getAllUsers().get(0);

        Response response = analytics.userActivityResponse(user.getId());

        assertAll(
                () -> assertTrue(response.jsonPath().getInt("loginsLastWeek") >= 0),
                () -> assertTrue(response.jsonPath().getInt("loginsLastMonth") >= response.jsonPath().getInt("loginsLastWeek"))
        );
    }

    @ParameterizedTest(name = "period = {0}")
    @ValueSource(strings = {"week", "month"})
    @DisplayName("Активность пользователя за week и month")
    void userActivityForPeriod(String period) {
        UserResponse user = userController.getAllUsers().get(0);

        Response response = analytics.userActivityResponse(user.getId(), period);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(period, response.jsonPath().getString("period")),
                () -> assertSeriesAreConsistent(response)
        );
    }

    @Test
    @DisplayName("За week 7 отсчётов")
    void weekHasSevenPoints() {
        UserResponse user = userController.getAllUsers().get(0);

        assertEquals(7, analytics.userActivityResponse(user.getId(), "week").jsonPath().getList("labels").size());
    }

    @Test
    @DisplayName("Пользователь без активности -> 200 и нули")
    void userWithoutActivity() {
        Response response = analytics.userActivityResponse(UNKNOWN_ID);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("logins", Integer.class).stream().mapToInt(Integer::intValue).sum()),
                () -> assertEquals(0, response.jsonPath().getInt("loginsLastMonth"))
        );
    }

    @Test
    @DisplayName("После входа вход виден в активности пользователя")
    void loginAppearsInUserActivity() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse user = userController.createUser(request);
        try {
            authController.login(request.getUsername(), request.getPassword());

            boolean appeared = Waiter.waitUntil(15, () -> analytics.userActivityResponse(user.getId(), "week").jsonPath().getInt("loginsLastWeek") >= 1);

            assertTrue(appeared, "Вход не появился в активности пользователя за 15 секунд");
        } finally {
            userController.deleteUserResponse(user.getId());
        }
    }

    @Test
    @DisplayName("Неизвестный period у пользователя -> 400")
    void userActivityWithInvalidPeriod() {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — period принимается любой и возвращается в ответе как есть, в отличие от dashboard
        Response response = analytics.userActivityResponse(UNKNOWN_ID, "bad");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("userId не UUID -> 400")
    void userActivityWithInvalidId() {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — формат id не проверяется
        Response response = analytics.userActivityResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/analytics/courses/{id}/activity ----------

    @Tag("smoke")
    @Test
    @DisplayName("Активность курса без period -> 200, период month, ряды одной длины")
    void courseActivityDefaults() {
        Response response = analytics.courseActivityResponse(UNKNOWN_ID);
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals("month", response.jsonPath().getString("period")),
                () -> assertSeriesAreConsistent(response)
        );
    }

    @ParameterizedTest(name = "period = {0}")
    @ValueSource(strings = {"week", "month"})
    @DisplayName("Активность курса за week и month")
    void courseActivityForPeriod(String period) {
        Response response = analytics.courseActivityResponse(UNKNOWN_ID, period);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(period, response.jsonPath().getString("period")),
                () -> assertSeriesAreConsistent(response)
        );
    }

    @Test
    @DisplayName("Неизвестный period у курса -> 400")
    void courseActivityWithInvalidPeriod() {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — period принимается любой
        Response response = analytics.courseActivityResponse(UNKNOWN_ID, "bad");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("courseId не UUID -> 400")
    void courseActivityWithInvalidId() {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — формат id не проверяется
        Response response = analytics.courseActivityResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }
}
