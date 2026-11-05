import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.GamificationController;
import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.request.GamificationSettings.PointRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Настройки геймификации общие на всё окружение: перед каждым тестом запоминаем их, после — возвращаем */
@Tag("regression")
@DisplayName("Gamification service: настройки, config и rules")
public class GamificationSettingsTest {

    private final GamificationController gamification = new GamificationController(Endpoints.GAMIFICATION_URL);
    private GamificationSettings original;

    @BeforeEach
    void rememberSettings() {
        original = gamification.getSettings();
    }

    @AfterEach
    void restoreSettings() {
        gamification.updateSettingsResponse(original);
    }

    /** Копия текущих настроек с другим правилом начисления за вход */
    private GamificationSettings withLoginPoints(int points) {
        GamificationSettings.Points changed = original.getPoints().toBuilder().login(new PointRule(true, points)).build();
        return original.toBuilder().points(changed).build();
    }

    // ---------- GET /api/gamification/settings ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /settings -> 200, заполнены points, badges, levels и leaderboard")
    void getSettings() {
        Response response = gamification.getSettingsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        GamificationSettings settings = response.as(GamificationSettings.class);
        assertAll(
                () -> assertEquals("default", settings.getId()),
                () -> assertNotNull(settings.getEnabled()),
                () -> assertNotNull(settings.getPoints().getLogin().getPoints()),
                () -> assertNotNull(settings.getPoints().getUnit().getPoints()),
                () -> assertNotNull(settings.getPoints().getCourse().getPoints()),
                () -> assertNotNull(settings.getPoints().getCertificate().getPoints()),
                () -> assertNotNull(settings.getPoints().getTest().getPoints()),
                () -> assertNotNull(settings.getBadges().getActivity()),
                () -> assertNotNull(settings.getLevels().getByPoints().getEvery()),
                () -> assertNotNull(settings.getLeaderboard().getShowPoints())
        );
    }

    // ---------- PUT /api/gamification/settings: positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление очков за вход -> 200, GET и /rules показывают новое значение")
    void updateLoginPoints() {
        Response response = gamification.updateSettingsResponse(withLoginPoints(12));
        assertEquals(200, response.statusCode(), response.asString());

        GamificationSettings fromGet = gamification.getSettings();
        Response rules = gamification.rulesResponse();
        assertAll(
                () -> assertEquals(12, response.as(GamificationSettings.class).getPoints().getLogin().getPoints()),
                () -> assertEquals(12, fromGet.getPoints().getLogin().getPoints()),
                () -> assertEquals(12, rules.jsonPath().getInt("points.find { it.action == 'Sign in' }.points"))
        );
    }

    @Test
    @DisplayName("Те же настройки обратно -> 200, ничего не меняется")
    void updateWithSameSettings() {
        Response response = gamification.updateSettingsResponse(original);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(original, gamification.getSettings())
        );
    }

    @ParameterizedTest(name = "points = {0}")
    @ValueSource(ints = {0, 1, 1000})
    @DisplayName("Граничные значения очков -> 200")
    void updateWithBoundaryPoints(int points) {
        Response response = gamification.updateSettingsResponse(withLoginPoints(points));

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(points, gamification.getSettings().getPoints().getLogin().getPoints())
        );
    }

    @Test
    @DisplayName("Выключение всей геймификации отражается в /config")
    void disableGamification() {
        gamification.updateSettingsResponse(original.toBuilder().enabled(false).build());

        Response config = gamification.configResponse();

        assertAll(
                () -> assertEquals(200, config.statusCode(), config.asString()),
                () -> assertFalse(config.jsonPath().getBoolean("enabled"))
        );
    }

    @Test
    @DisplayName("Выключение очков, бейджей и уровней отражается в /config")
    void disableFeatures() {
        gamification.updateSettingsResponse(original.toBuilder()
                .points(original.getPoints().toBuilder().enabled(false).build())
                .badges(original.getBadges().toBuilder().enabled(false).build())
                .levels(original.getLevels().toBuilder().enabled(false).build())
                .build());

        Response config = gamification.configResponse();

        assertAll(
                () -> assertFalse(config.jsonPath().getBoolean("points")),
                () -> assertFalse(config.jsonPath().getBoolean("badges")),
                () -> assertFalse(config.jsonPath().getBoolean("levels"))
        );
    }

    @Test
    @DisplayName("Настройки рейтинга отражаются в /config")
    void leaderboardSettingsAreReflectedInConfig() {
        gamification.updateSettingsResponse(original.toBuilder()
                .leaderboard(new GamificationSettings.Leaderboard(true, false, false, false)).build());

        Response config = gamification.configResponse();

        assertAll(
                () -> assertTrue(config.jsonPath().getBoolean("leaderboard.enabled")),
                () -> assertFalse(config.jsonPath().getBoolean("leaderboard.showLevels")),
                () -> assertFalse(config.jsonPath().getBoolean("leaderboard.showPoints")),
                () -> assertFalse(config.jsonPath().getBoolean("leaderboard.showBadges"))
        );
    }

    @Test
    @DisplayName("Шаг уровней (every) сохраняется и виден в /rules")
    void updateLevelStep() {
        GamificationSettings changed = original.toBuilder().levels(original.getLevels().toBuilder()
                .byPoints(new GamificationSettings.LevelRule(true, 100)).build()).build();

        Response response = gamification.updateSettingsResponse(changed);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(100, gamification.getSettings().getLevels().getByPoints().getEvery()),
                () -> assertEquals(100, gamification.rulesResponse().jsonPath().getInt("levels.find { it.level == 2 }.points"))
        );
    }

    // ---------- PUT: negative ----------

    @ParameterizedTest(name = "points = {0} -> 400")
    @ValueSource(ints = {-1, -100})
    @DisplayName("Отрицательные очки -> 400")
    void updateWithNegativePoints(int points) {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — отрицательные очки принимаются и сохраняются
        Response response = gamification.updateSettingsResponse(withLoginPoints(points));

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "every = {0} -> 400")
    @ValueSource(ints = {0, -1})
    @DisplayName("Шаг уровней меньше 1 -> 400")
    void updateWithInvalidLevelStep(int every) {
        GamificationSettings changed = original.toBuilder().levels(original.getLevels().toBuilder()
                .byPoints(new GamificationSettings.LevelRule(true, every)).build()).build();

        // ПАДАЕТ (баг бэка), если вернётся 200: шаг 0 приводит к делению на ноль при расчёте уровня
        Response response = gamification.updateSettingsResponse(changed);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Пустой объект {} -> 400")
    void updateWithEmptyObject() {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — пустое тело принимается, подставляются значения по умолчанию
        Response response = gamification.updateSettingsRaw("{}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "not a json", "[]", "{\"enabled\":\"yes\"}", "{\"points\":\"many\"}"})
    @DisplayName("Некорректное тело -> 400")
    void updateWithMalformedBody(String body) {
        // ПАДАЕТ (баг бэка): 500 вместо 400 — нечитаемый JSON не обрабатывается, в других сервисах это 400
        Response response = gamification.updateSettingsRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- POST /api/gamification/settings/reset ----------

    @Test
    @DisplayName("Сброс настроек -> 200, значения по умолчанию: 10/15/100/50/20 очков, уровни каждые 300/5/5")
    void resetSettings() {
        gamification.updateSettingsResponse(withLoginPoints(77));

        Response response = gamification.resetSettingsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        GamificationSettings reset = gamification.getSettings();
        assertAll(
                () -> assertEquals(reset, response.as(GamificationSettings.class)),
                () -> assertEquals(10, reset.getPoints().getLogin().getPoints()),
                () -> assertEquals(15, reset.getPoints().getUnit().getPoints()),
                () -> assertEquals(100, reset.getPoints().getCourse().getPoints()),
                () -> assertEquals(50, reset.getPoints().getCertificate().getPoints()),
                () -> assertEquals(20, reset.getPoints().getTest().getPoints()),
                () -> assertEquals(300, reset.getLevels().getByPoints().getEvery()),
                () -> assertEquals(5, reset.getLevels().getByCourses().getEvery()),
                () -> assertEquals(5, reset.getLevels().getByBadges().getEvery()),
                () -> assertTrue(reset.getEnabled())
        );
    }

    // ---------- GET /api/gamification/config ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /config -> 200, флаги совпадают с настройками")
    void getConfig() {
        Response response = gamification.configResponse();
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals(original.getEnabled(), response.jsonPath().getBoolean("enabled")),
                () -> assertEquals(original.getPoints().getEnabled(), response.jsonPath().getBoolean("points")),
                () -> assertEquals(original.getBadges().getEnabled(), response.jsonPath().getBoolean("badges")),
                () -> assertEquals(original.getLevels().getEnabled(), response.jsonPath().getBoolean("levels")),
                () -> assertEquals(original.getLeaderboard().getEnabled(), response.jsonPath().getBoolean("leaderboard.enabled"))
        );
    }

    // ---------- GET /api/gamification/rules ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /rules -> 200, пять правил начисления совпадают с настройками, есть таблица уровней")
    void getRules() {
        Response response = gamification.rulesResponse();
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals(5, response.jsonPath().getList("points").size()),
                () -> assertEquals(original.getPoints().getLogin().getPoints(), response.jsonPath().getInt("points.find { it.action == 'Sign in' }.points")),
                () -> assertEquals(original.getPoints().getUnit().getPoints(), response.jsonPath().getInt("points.find { it.action == 'Complete a unit' }.points")),
                () -> assertEquals(original.getPoints().getCourse().getPoints(), response.jsonPath().getInt("points.find { it.action == 'Complete a course' }.points")),
                () -> assertEquals(original.getPoints().getCertificate().getPoints(), response.jsonPath().getInt("points.find { it.action == 'Get a certificate' }.points")),
                () -> assertEquals(original.getPoints().getTest().getPoints(), response.jsonPath().getInt("points.find { it.action == 'Pass a test' }.points")),
                () -> assertEquals(1, response.jsonPath().getInt("levels[0].level")),
                () -> assertEquals(0, response.jsonPath().getInt("levels[0].points")),
                () -> assertEquals(original.getLevels().getByPoints().getEvery(), response.jsonPath().getInt("levels[1].points"))
        );
    }
}
