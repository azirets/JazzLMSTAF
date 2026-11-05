import io.restassured.response.Response;
import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.request.GamificationSettings.Leaderboard;
import kg.jazzlms.models.response.LeaderboardResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: настройки рейтинга. leaderboard.enabled и showLevels / showPoints / showBadges видны фронту в /config,
 * и по ним он решает, что рисовать. Сам эндпоинт /leaderboard при этом отдаёт данные всегда
 * (это поведение сервиса на момент написания теста — тесты ниже его фиксируют).
 */
@Tag("e2e")
@DisplayName("E2E gamification: рейтинг — настройки в /config и поведение /leaderboard")
public class GamificationLeaderboardSettingsE2ETest extends GamificationE2ESupport {

    private void applyLeaderboard(Leaderboard leaderboard) {
        apply(original.toBuilder().leaderboard(leaderboard).build());
    }

    @Test
    @DisplayName("Рейтинг включён, показываем всё: /config отражает все четыре флага")
    void configShowsLeaderboardEnabled() {
        applyLeaderboard(new Leaderboard(true, true, true, true));

        Response config = GAMIFICATION.configResponse();

        assertAll(
                () -> assertEquals(200, config.statusCode(), config.asString()),
                () -> assertTrue(config.jsonPath().getBoolean("leaderboard.enabled")),
                () -> assertTrue(config.jsonPath().getBoolean("leaderboard.showLevels")),
                () -> assertTrue(config.jsonPath().getBoolean("leaderboard.showPoints")),
                () -> assertTrue(config.jsonPath().getBoolean("leaderboard.showBadges"))
        );
    }

    @Test
    @DisplayName("leaderboard.enabled = false: /config показывает рейтинг выключенным, остальные флаги не трогает")
    void configShowsLeaderboardDisabled() {
        applyLeaderboard(new Leaderboard(false, true, true, true));

        Response config = GAMIFICATION.configResponse();

        assertAll(
                () -> assertFalse(config.jsonPath().getBoolean("leaderboard.enabled")),
                () -> assertTrue(config.jsonPath().getBoolean("leaderboard.showLevels")),
                () -> assertTrue(config.jsonPath().getBoolean("enabled"), "Общий флаг геймификации не меняется")
        );
    }

    @ParameterizedTest(name = "показ: уровни={0}, очки={1}, бейджи={2}")
    @CsvSource({"false, true, true", "true, false, true", "true, true, false", "false, false, false"})
    @DisplayName("Флаги show* отражаются в /config по отдельности")
    void configShowsEachShowFlag(boolean levels, boolean points, boolean badges) {
        applyLeaderboard(new Leaderboard(true, levels, points, badges));

        Response config = GAMIFICATION.configResponse();

        assertAll(
                () -> assertEquals(levels, config.jsonPath().getBoolean("leaderboard.showLevels")),
                () -> assertEquals(points, config.jsonPath().getBoolean("leaderboard.showPoints")),
                () -> assertEquals(badges, config.jsonPath().getBoolean("leaderboard.showBadges"))
        );
    }

    @Test
    @DisplayName("Рейтинг выключен: эндпоинт /leaderboard всё равно отдаёт данные (скрывает рейтинг фронт по /config)")
    void leaderboardEndpointStillServesWhenDisabled() {
        applyLeaderboard(new Leaderboard(false, true, true, true));

        Response response = GAMIFICATION.leaderboardResponse(Map.of("limit", 3));

        LeaderboardResponse board = response.as(LeaderboardResponse.class);
        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(3, board.getTop().size()),
                () -> assertNotNull(board.getMe())
        );
    }

    @Test
    @DisplayName("Флаги show* выключены: эндпоинт /leaderboard по-прежнему отдаёт очки (прячет их фронт)")
    void leaderboardEndpointStillReturnsScores() {
        applyLeaderboard(new Leaderboard(true, false, false, false));

        LeaderboardResponse board = GAMIFICATION.leaderboard(Map.of("limit", 3));

        assertAll(
                () -> assertEquals(3, board.getTop().size()),
                () -> assertTrue(board.getTop().stream().allMatch(e -> e.getScore() >= 0 && e.getUserId() != null))
        );
    }

    @Test
    @DisplayName("Геймификация выключена целиком: /leaderboard продолжает работать, /config показывает enabled = false")
    void leaderboardWhenGamificationDisabled() {
        apply(original.toBuilder().enabled(false).build());

        Response leaderboard = GAMIFICATION.leaderboardResponse(Map.of("limit", 3));
        Response config = GAMIFICATION.configResponse();

        assertAll(
                () -> assertEquals(200, leaderboard.statusCode(), leaderboard.asString()),
                () -> assertFalse(config.jsonPath().getBoolean("enabled"))
        );
    }

    @Test
    @DisplayName("Ученик с очками получает место в рейтинге")
    void userWithPointsHasRank() {
        GamificationSettings.Leaderboard on = new Leaderboard(true, true, true, true);
        applyLeaderboard(on);

        Run earned = play(true);

        assertAll(
                () -> assertTrue(earned.profile.getPoints() > 0),
                () -> assertNotNull(earned.profile.getRank(), "У ученика с очками должно быть место"),
                () -> assertTrue(earned.profile.getRank() >= 1)
        );
    }
}
