import io.restassured.response.Response;
import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.request.GamificationSettings.PointRule;
import kg.jazzlms.models.request.GamificationSettings.Points;
import kg.jazzlms.models.response.UserGamificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: начисление очков при разных настройках — выключены все очки, выключено отдельное правило,
 * заданы свои значения. Счётчики (входы, юниты, курсы) и бейджи от очков не зависят.
 */
@Tag("e2e")
@DisplayName("E2E gamification: очки — выключены все, отдельные правила, свои значения")
public class GamificationPointsE2ETest extends GamificationE2ESupport {

    /** Действие в истории для каждого правила */
    private static final Map<String, String> ACTION_OF_RULE = Map.of(
            "login", "Sign in",
            "unit", "Unit completed",
            "test", "Test passed",
            "course", "Course completed",
            "certificate", "Certificate");

    /** Копия очков, где правило выключено, но его значение сохранено */
    private Points withRuleDisabled(String rule) {
        Points p = original.getPoints();
        return switch (rule) {
            case "login" -> p.toBuilder().login(new PointRule(false, p.getLogin().getPoints())).build();
            case "unit" -> p.toBuilder().unit(new PointRule(false, p.getUnit().getPoints())).build();
            case "test" -> p.toBuilder().test(new PointRule(false, p.getTest().getPoints())).build();
            case "course" -> p.toBuilder().course(new PointRule(false, p.getCourse().getPoints())).build();
            case "certificate" -> p.toBuilder().certificate(new PointRule(false, p.getCertificate().getPoints())).build();
            default -> throw new IllegalArgumentException(rule);
        };
    }

    // ---------------------------------------------------------------- начисление включено (эталон)

    @Tag("smoke")
    @Test
    @DisplayName("Все правила включены: очки = вход + 2 юнита + тест + курс + сертификат, каждое начисление ровно один раз")
    void allRulesEnabled() {
        GamificationSettings.Points p = original.getPoints();

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertEquals(expectedPoints(original), profile.getPoints()),
                () -> assertEquals(p.getLogin().getPoints() + 2 * p.getUnit().getPoints() + p.getTest().getPoints()
                        + p.getCourse().getPoints() + p.getCertificate().getPoints(), profile.getPoints()),
                () -> assertEquals(1, profile.getLogins()),
                () -> assertEquals(2, profile.getUnits()),
                () -> assertEquals(1, profile.getCourses()),
                () -> assertEquals(1, historyEntries(profile, "Sign in").size()),
                () -> assertEquals(2, historyEntries(profile, "Unit completed").size()),
                () -> assertEquals(1, historyEntries(profile, "Test passed").size()),
                () -> assertEquals(1, historyEntries(profile, "Course completed").size()),
                () -> assertEquals(1, historyEntries(profile, "Certificate").size()),
                () -> assertEquals(profile.getPoints(), profile.getHistory().stream()
                        .mapToInt(h -> ((Number) h.get("points")).intValue()).sum(), "Сумма истории должна равняться очкам")
        );
    }

    // ---------------------------------------------------------------- выключены все очки

    @Test
    @DisplayName("points.enabled = false: очков 0, но счётчики, бейджи и записи в истории (с 0 очков) остаются")
    void pointsDisabled() {
        apply(original.toBuilder().points(original.getPoints().toBuilder().enabled(false).build()).build());

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertTrue(profile.isEnabled()),
                () -> assertEquals(0, profile.getPoints()),
                () -> assertEquals(1, profile.getLogins()),
                () -> assertEquals(2, profile.getUnits()),
                () -> assertEquals(1, profile.getCourses()),
                () -> assertEquals(3, profile.getBadges().size(), "Бейджи от очков не зависят"),
                () -> assertEquals(1, profile.getLevel(), "Без очков уровень 1"),
                () -> assertFalse(profile.getHistory().isEmpty(), "Действия в истории остаются"),
                () -> ACTION_OF_RULE.values().forEach(action ->
                        assertEquals(0, historyPoints(profile, action), "Очки за '" + action + "' должны быть 0"))
        );
    }

    @Test
    @DisplayName("points.enabled = false: /config показывает points = false")
    void configShowsPointsDisabled() {
        apply(original.toBuilder().points(original.getPoints().toBuilder().enabled(false).build()).build());

        Response config = GAMIFICATION.configResponse();

        assertAll(
                () -> assertEquals(200, config.statusCode(), config.asString()),
                () -> assertFalse(config.jsonPath().getBoolean("points")),
                () -> assertTrue(config.jsonPath().getBoolean("enabled"))
        );
    }

    // ---------------------------------------------------------------- выключено отдельное правило

    @ParameterizedTest(name = "выключено правило: {0}")
    @ValueSource(strings = {"login", "unit", "test", "course", "certificate"})
    @DisplayName("Выключено одно правило: за него 0 очков (запись в истории остаётся), остальные начисляются как обычно")
    void singleRuleDisabled(String rule) {
        GamificationSettings changed = withPoints(withRuleDisabled(rule));
        apply(changed);

        UserGamificationResponse profile = play(true).profile;

        String disabledAction = ACTION_OF_RULE.get(rule);
        assertAll(
                () -> assertEquals(expectedPoints(changed), profile.getPoints(), "Очки без правила '" + rule + "'"),
                () -> assertTrue(expectedPoints(changed) < expectedPoints(original), "Проверка теста: очки должны уменьшиться"),
                () -> assertFalse(historyEntries(profile, disabledAction).isEmpty(), "Действие '" + disabledAction + "' должно остаться в истории"),
                () -> assertEquals(0, historyPoints(profile, disabledAction), "За выключенное правило очков нет"),
                () -> ACTION_OF_RULE.entrySet().stream().filter(e -> !e.getKey().equals(rule)).forEach(e ->
                        assertTrue(historyPoints(profile, e.getValue()) > 0, "Правило '" + e.getKey() + "' включено, очки должны быть")),
                () -> assertEquals(1, profile.getLogins(), "Счётчики от правил не зависят"),
                () -> assertEquals(2, profile.getUnits()),
                () -> assertEquals(1, profile.getCourses())
        );
    }

    @Test
    @DisplayName("Выключены все пять правил по отдельности: очков 0, действия учитываются")
    void allRulesDisabledOneByOne() {
        Points p = original.getPoints();
        Points allOff = p.toBuilder()
                .login(new PointRule(false, p.getLogin().getPoints()))
                .unit(new PointRule(false, p.getUnit().getPoints()))
                .test(new PointRule(false, p.getTest().getPoints()))
                .course(new PointRule(false, p.getCourse().getPoints()))
                .certificate(new PointRule(false, p.getCertificate().getPoints()))
                .build();
        apply(withPoints(allOff));

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertEquals(0, profile.getPoints()),
                () -> assertEquals(2, profile.getUnits()),
                () -> assertEquals(1, profile.getCourses())
        );
    }

    // ---------------------------------------------------------------- свои значения

    @Test
    @DisplayName("Свои значения (вход 25, юнит 5, тест 7, курс 300, сертификат 1): очки считаются по ним")
    void customPointValues() {
        Points custom = original.getPoints().toBuilder()
                .login(new PointRule(true, 25)).unit(new PointRule(true, 5)).test(new PointRule(true, 7))
                .course(new PointRule(true, 300)).certificate(new PointRule(true, 1)).build();
        GamificationSettings changed = withPoints(custom);
        apply(changed);

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertEquals(25 + 2 * 5 + 7 + 300 + 1, profile.getPoints()),
                () -> assertEquals(expectedPoints(changed), profile.getPoints()),
                () -> assertEquals(25, historyPoints(profile, "Sign in")),
                () -> assertEquals(10, historyPoints(profile, "Unit completed")),
                () -> assertEquals(7, historyPoints(profile, "Test passed")),
                () -> assertEquals(300, historyPoints(profile, "Course completed")),
                () -> assertEquals(1, historyPoints(profile, "Certificate"))
        );
    }

    @Test
    @DisplayName("Правила включены, но значения 0: очков 0, действия учитываются")
    void zeroPointValues() {
        Points zero = original.getPoints().toBuilder()
                .login(new PointRule(true, 0)).unit(new PointRule(true, 0)).test(new PointRule(true, 0))
                .course(new PointRule(true, 0)).certificate(new PointRule(true, 0)).build();
        apply(withPoints(zero));

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertEquals(0, profile.getPoints()),
                () -> assertEquals(1, profile.getCourses()),
                () -> assertEquals(3, profile.getBadges().size())
        );
    }
}
