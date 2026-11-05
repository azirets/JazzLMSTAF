import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AnalyticsController;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.TopUserResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import kg.jazzlms.utils.Waiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Analytics service: summary, overview, dashboard, top-users")
public class AnalyticsDashboardTest {

    private final AnalyticsController analytics = new AnalyticsController(Endpoints.ANALYTICS_URL);
    private final UserController userController = new UserController(Endpoints.BASE_URL);
    private final AuthController authController = new AuthController(Endpoints.BASE_URL);

    // ---------- GET /api/analytics/summary ----------

    @Tag("smoke")
    @Test
    @DisplayName("summary -> 200, today и week с тремя метриками: value, previous, changePercent")
    void summary() {
        Response response = analytics.summaryResponse();
        assertEquals(200, response.statusCode(), response.asString());

        for (String period : List.of("today", "week")) {
            for (String metric : List.of("usersWithActivity", "courseCompletions", "logins")) {
                String path = period + "." + metric;
                assertAll(path,
                        () -> assertNotNull(response.jsonPath().get(path + ".value"), path + ".value"),
                        () -> assertNotNull(response.jsonPath().get(path + ".previous"), path + ".previous"),
                        () -> assertNotNull(response.jsonPath().get(path + ".changePercent"), path + ".changePercent"),
                        () -> assertTrue(response.jsonPath().getInt(path + ".value") >= 0),
                        () -> assertTrue(response.jsonPath().getInt(path + ".previous") >= 0)
                );
            }
        }
    }

    @Test
    @DisplayName("summary: значение за неделю не меньше значения за сегодня")
    void weekIsNotLessThanToday() {
        Response response = analytics.summaryResponse();

        for (String metric : List.of("usersWithActivity", "courseCompletions", "logins")) {
            assertTrue(response.jsonPath().getInt("week." + metric + ".value") >= response.jsonPath().getInt("today." + metric + ".value"),
                    metric + ": за неделю меньше, чем за сегодня");
        }
    }

    // ---------- GET /api/analytics/overview ----------

    @Tag("smoke")
    @Test
    @DisplayName("overview -> 200, есть topUsers и eventsByType")
    void overview() {
        Response response = analytics.overviewResponse();
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertNotNull(response.jsonPath().getList("topUsers")),
                () -> assertNotNull(response.jsonPath().getMap("eventsByType")),
                () -> assertFalse(response.jsonPath().getMap("eventsByType").isEmpty(), "eventsByType пустой")
        );
    }

    @Test
    @DisplayName("overview.eventsByType: все значения — неотрицательные числа")
    void overviewEventCountsAreNonNegative() {
        Response response = analytics.overviewResponse();

        response.jsonPath().getMap("eventsByType").values()
                .forEach(v -> assertTrue(((Number) v).longValue() >= 0, "Отрицательный счётчик: " + v));
    }

    // ---------- GET /api/analytics/dashboard ----------

    @Tag("smoke")
    @Test
    @DisplayName("dashboard без period -> 200, период today, 24 часовых отсчёта")
    void dashboardDefaultsToToday() {
        Response response = analytics.dashboardResponse();
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals("today", response.jsonPath().getString("period")),
                () -> assertEquals(24, response.jsonPath().getList("labels").size()),
                () -> assertEquals(24, response.jsonPath().getList("logins").size()),
                () -> assertEquals(24, response.jsonPath().getList("completions").size())
        );
    }

    @ParameterizedTest(name = "period = {0}")
    @ValueSource(strings = {"today", "yesterday", "week", "month"})
    @DisplayName("Каждый period -> 200, ряды той же длины, что и labels, итоги равны суммам рядов")
    void dashboardForEachPeriod(String period) {
        Response response = analytics.dashboardResponse(period);
        assertEquals(200, response.statusCode(), response.asString());

        List<Integer> logins = response.jsonPath().getList("logins");
        List<Integer> completions = response.jsonPath().getList("completions");
        int labels = response.jsonPath().getList("labels").size();
        assertAll(
                () -> assertEquals(period, response.jsonPath().getString("period")),
                () -> assertTrue(labels > 0),
                () -> assertEquals(labels, logins.size()),
                () -> assertEquals(labels, completions.size()),
                () -> assertEquals(logins.stream().mapToInt(Integer::intValue).sum(), response.jsonPath().getInt("totals.logins")),
                () -> assertEquals(completions.stream().mapToInt(Integer::intValue).sum(), response.jsonPath().getInt("totals.completions"))
        );
    }

    @Test
    @DisplayName("Количество отсчётов: week = 7, yesterday = 24")
    void dashboardSeriesLength() {
        assertAll(
                () -> assertEquals(7, analytics.dashboardResponse("week").jsonPath().getList("labels").size()),
                () -> assertEquals(24, analytics.dashboardResponse("yesterday").jsonPath().getList("labels").size())
        );
    }

    @ParameterizedTest(name = "period = {0} -> 400")
    @ValueSource(strings = {"bad", "year", "TODAY", "1"})
    @DisplayName("Неизвестный period -> 400")
    void dashboardWithInvalidPeriod(String period) {
        Response response = analytics.dashboardResponse(period);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("После входа пользователя итог logins за сегодня растёт")
    void loginsGrowAfterLogin() {
        int before = analytics.dashboardResponse("today").jsonPath().getInt("totals.logins");
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse user = userController.createUser(request);
        try {
            authController.login(request.getUsername(), request.getPassword());

            boolean grew = Waiter.waitUntil(15,
                    () -> analytics.dashboardResponse("today").jsonPath().getInt("totals.logins") > before);
            assertTrue(grew, "Вход пользователя не попал в статистику за 15 секунд");
        } finally {
            userController.deleteUserResponse(user.getId());
        }
    }

    // ---------- GET /api/analytics/top-users ----------

    @Tag("smoke")
    @Test
    @DisplayName("top-users -> 200, список отсортирован по score по убыванию")
    void topUsersAreSorted() {
        Response response = analytics.topUsersResponse();
        assertEquals(200, response.statusCode(), response.asString());

        List<TopUserResponse> users = Arrays.asList(response.as(TopUserResponse[].class));
        assertAll(
                () -> assertFalse(users.isEmpty()),
                () -> assertTrue(users.stream().allMatch(u -> u.getUserId() != null && u.getScore() >= 0)),
                () -> {
                    for (int i = 1; i < users.size(); i++) {
                        assertTrue(users.get(i - 1).getScore() >= users.get(i).getScore(), "Нарушен порядок на позиции " + i);
                    }
                }
        );
    }

    @ParameterizedTest(name = "limit = {0}")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("limit ограничивает число пользователей")
    void topUsersWithLimit(int limit) {
        Response response = analytics.topUsersResponse(limit);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertTrue(response.as(TopUserResponse[].class).length <= limit),
                () -> assertEquals(limit, response.as(TopUserResponse[].class).length, "Пользователей достаточно, ожидали ровно limit")
        );
    }

    @Test
    @DisplayName("Большой limit -> 200")
    void topUsersWithBigLimit() {
        Response response = analytics.topUsersResponse(1000);

        assertEquals(200, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "limit = {0} -> 400")
    @ValueSource(strings = {"abc", "1.5"})
    @DisplayName("limit не число -> 400")
    void topUsersWithNonNumericLimit(String limit) {
        Response response = analytics.topUsersResponse(limit);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "limit = {0} -> 400")
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("limit меньше 1 -> 400")
    void topUsersWithNonPositiveLimit(String limit) {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — limit <= 0 игнорируется и возвращается весь список
        Response response = analytics.topUsersResponse(limit);

        assertEquals(400, response.statusCode(), response.asString());
    }
}
