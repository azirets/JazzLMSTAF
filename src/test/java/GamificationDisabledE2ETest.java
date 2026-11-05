import io.restassured.response.Response;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.models.response.UserGamificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: геймификация выключена целиком (settings.enabled = false).
 * Пока она выключена, сервис не учитывает вообще ничего: ни очки, ни счётчики, ни бейджи, ни историю.
 */
@Tag("e2e")
@DisplayName("E2E gamification: геймификация выключена (enabled = false)")
public class GamificationDisabledE2ETest extends GamificationE2ESupport {

    private void disable() {
        apply(original.toBuilder().enabled(false).build());
    }

    @Test
    @DisplayName("Ученик проходит весь путь: очков, счётчиков, бейджей и истории нет")
    void nothingIsRecordedWhileDisabled() {
        disable();

        Run run = play(false);

        UserGamificationResponse profile = run.profile;
        assertAll(
                () -> assertFalse(profile.isEnabled(), "В профиле должно быть enabled = false"),
                () -> assertEquals(0, profile.getPoints()),
                () -> assertEquals(0, profile.getLogins()),
                () -> assertEquals(0, profile.getUnits()),
                () -> assertEquals(0, profile.getCourses()),
                () -> assertEquals(1, profile.getLevel()),
                () -> assertTrue(profile.getBadges().isEmpty(), "Бейджи не должны выдаваться"),
                () -> assertTrue(profile.getHistory().isEmpty(), "История не должна вестись"),
                () -> assertNull(profile.getRank(), "Без очков нет места в рейтинге")
        );
    }

    @Test
    @DisplayName("/config показывает enabled = false")
    void configShowsDisabled() {
        disable();

        Response config = GAMIFICATION.configResponse();

        assertAll(
                () -> assertEquals(200, config.statusCode(), config.asString()),
                () -> assertFalse(config.jsonPath().getBoolean("enabled"))
        );
    }

    @Test
    @DisplayName("Эндпоинты чтения продолжают работать: /me, /rules, /leaderboard, /settings -> 200")
    void readEndpointsStillWork() {
        disable();

        Response me = GAMIFICATION.meResponse();
        Response rules = GAMIFICATION.rulesResponse();
        Response leaderboard = GAMIFICATION.leaderboardResponse();
        Response settings = GAMIFICATION.getSettingsResponse();

        assertAll(
                () -> assertEquals(200, me.statusCode(), me.asString()),
                () -> assertFalse(me.jsonPath().getBoolean("enabled")),
                () -> assertEquals(200, rules.statusCode(), rules.asString()),
                () -> assertEquals(200, leaderboard.statusCode(), leaderboard.asString()),
                () -> assertEquals(200, settings.statusCode(), settings.asString()),
                () -> assertFalse(settings.jsonPath().getBoolean("enabled"))
        );
    }

    @Test
    @DisplayName("Накопленное до выключения сохраняется и дальше не меняется")
    void earnedDataIsKeptButNotChangedWhileDisabled() {
        Run run = play(true);
        UserGamificationResponse before = run.profile;
        assertTrue(before.getPoints() > 0, "До выключения ученик должен был набрать очки");

        disable();
        completeExtraLesson(run);
        sleepQuietly();

        UserGamificationResponse after = GAMIFICATION.user(run.userId);
        assertAll(
                () -> assertEquals(before.getPoints(), after.getPoints(), "Очки не должны меняться"),
                () -> assertEquals(before.getUnits(), after.getUnits(), "Счётчик юнитов не должен меняться"),
                () -> assertEquals(before.getHistory().size(), after.getHistory().size(), "История не должна пополняться"),
                () -> assertEquals(before.getBadges().size(), after.getBadges().size())
        );
    }

    @Test
    @DisplayName("Включили обратно: новые действия снова учитываются, а то, что было при выключенной, нет")
    void eventsAreCountedAgainAfterEnabling() {
        disable();
        Run run = play(false);
        assertEquals(0, run.profile.getPoints(), "Пока выключено, очков нет");

        apply(original); // включаем обратно
        completeExtraLesson(run);

        int unitPoints = original.getPoints().getUnit().getPoints();
        boolean counted = kg.jazzlms.utils.Waiter.waitUntil(WAIT_SECONDS, () -> GAMIFICATION.user(run.userId).getUnits() == 1);
        UserGamificationResponse profile = GAMIFICATION.user(run.userId);
        assertAll(
                () -> assertTrue(counted, "Урок после включения не учтён за " + WAIT_SECONDS + " секунд"),
                () -> assertTrue(profile.isEnabled()),
                () -> assertEquals(1, profile.getUnits(), "Учтён только урок, пройденный после включения"),
                () -> assertEquals(unitPoints + original.getPoints().getCourse().getPoints() + original.getPoints().getCertificate().getPoints(),
                        profile.getPoints(), "Очки за урок и завершённый после него курс с сертификатом"),
                () -> assertEquals(0, profile.getLogins(), "Вход, сделанный при выключенной геймификации, не учитывается")
        );
    }

    @Test
    @DisplayName("Вход при выключенной геймификации очков не даёт, а вход после включения даёт")
    void loginPointsAfterEnabling() {
        disable();
        Run run = play(false);
        assertEquals(0, run.profile.getLogins());

        apply(original);
        new AuthController(kg.jazzlms.constants.Endpoints.BASE_URL).login(run.request.getUsername(), run.request.getPassword());

        int loginPoints = original.getPoints().getLogin().getPoints();
        boolean counted = kg.jazzlms.utils.Waiter.waitUntil(WAIT_SECONDS, () -> GAMIFICATION.user(run.userId).getLogins() == 1);
        UserGamificationResponse profile = GAMIFICATION.user(run.userId);
        assertAll(
                () -> assertTrue(counted, "Вход после включения не учтён за " + WAIT_SECONDS + " секунд"),
                () -> assertEquals(loginPoints, profile.getPoints())
        );
    }

    private static void sleepQuietly() {
        kg.jazzlms.utils.Waiter.waitUntil(QUIET_SECONDS, () -> false);
    }
}
