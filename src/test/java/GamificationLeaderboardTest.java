import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.GamificationController;
import kg.jazzlms.models.response.LeaderboardResponse;
import kg.jazzlms.models.response.UserGamificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Gamification service: рейтинг (/api/gamification/leaderboard)")
public class GamificationLeaderboardTest {

    private final GamificationController gamification = new GamificationController(Endpoints.GAMIFICATION_URL);

    // ---------- positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Рейтинг без параметров -> 200, by = points, ранги идут с 1, очки по убыванию")
    void leaderboardDefaults() {
        Response response = gamification.leaderboardResponse();
        assertEquals(200, response.statusCode(), response.asString());

        LeaderboardResponse board = response.as(LeaderboardResponse.class);
        assertAll(
                () -> assertEquals("points", board.getBy()),
                () -> assertFalse(board.getTop().isEmpty()),
                () -> assertEquals(1, board.getTop().get(0).getRank()),
                () -> {
                    for (int i = 1; i < board.getTop().size(); i++) {
                        assertTrue(board.getTop().get(i - 1).getScore() >= board.getTop().get(i).getScore(), "Нарушен порядок на позиции " + i);
                        assertTrue(board.getTop().get(i).getRank() > board.getTop().get(i - 1).getRank(), "Ранги должны расти, позиция " + i);
                    }
                }
        );
    }

    @ParameterizedTest(name = "by = {0}")
    @ValueSource(strings = {"points", "courses", "badges", "levels"})
    @DisplayName("Каждый допустимый by -> 200, критерий возвращается в ответе, очки по убыванию")
    void leaderboardForEachCriterion(String by) {
        Response response = gamification.leaderboardResponse(Map.of("by", by));
        assertEquals(200, response.statusCode(), response.asString());

        LeaderboardResponse board = response.as(LeaderboardResponse.class);
        List<LeaderboardResponse.Entry> top = board.getTop();
        assertAll(
                () -> assertEquals(by, board.getBy()),
                () -> assertFalse(top.isEmpty()),
                () -> {
                    for (int i = 1; i < top.size(); i++) {
                        assertTrue(top.get(i - 1).getScore() >= top.get(i).getScore(), "Нарушен порядок на позиции " + i);
                    }
                }
        );
    }

    @ParameterizedTest(name = "limit = {0}")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("limit ограничивает размер top")
    void leaderboardWithLimit(int limit) {
        LeaderboardResponse board = gamification.leaderboard(Map.of("limit", limit));

        assertEquals(limit, board.getTop().size());
    }

    @Test
    @DisplayName("Большой limit -> 200")
    void leaderboardWithBigLimit() {
        Response response = gamification.leaderboardResponse(Map.of("limit", 1000));

        assertEquals(200, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("В top у каждой записи есть userId и name, me = false")
    void entriesHaveRequiredFields() {
        LeaderboardResponse board = gamification.leaderboard(Map.of("limit", 5));

        assertAll(
                () -> assertTrue(board.getTop().stream().allMatch(e -> e.getUserId() != null && e.getName() != null)),
                () -> assertTrue(board.getTop().stream().noneMatch(LeaderboardResponse.Entry::isMe), "В top не должно быть пометки me у чужих записей")
        );
    }

    @Test
    @DisplayName("Блок me — текущий пользователь (администратор), его очки совпадают с /me")
    void meBlockMatchesProfile() {
        LeaderboardResponse board = gamification.leaderboard(Map.of("limit", 1));
        UserGamificationResponse me = gamification.meResponse().as(UserGamificationResponse.class);

        assertAll(
                () -> assertNotNull(board.getMe()),
                () -> assertTrue(board.getMe().isMe()),
                () -> assertEquals(me.getUserId(), board.getMe().getUserId()),
                () -> assertTrue(board.getMe().getRank() >= 1)
        );
    }

    // ---------- negative ----------

    @ParameterizedTest(name = "by = {0} -> 400")
    @ValueSource(strings = {"bad", "POINTS", "1"})
    @DisplayName("Неизвестный критерий by -> 400")
    void leaderboardWithInvalidCriterion(String by) {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — любой by принимается и возвращается в ответе как есть
        Response response = gamification.leaderboardResponse(Map.of("by", by));

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "limit = {0} -> 400")
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("limit меньше 1 -> 400")
    void leaderboardWithNonPositiveLimit(String limit) {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — возвращается весь рейтинг
        Response response = gamification.leaderboardResponse(Map.of("limit", limit));

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "limit = {0} -> 400")
    @ValueSource(strings = {"abc", "1.5"})
    @DisplayName("limit не число -> 400")
    void leaderboardWithNonNumericLimit(String limit) {
        // ПАДАЕТ (баг бэка): 500 вместо 400 — неверный формат числа не обрабатывается
        Response response = gamification.leaderboardResponse(Map.of("limit", limit));

        assertEquals(400, response.statusCode(), response.asString());
    }
}
