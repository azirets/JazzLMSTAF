package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.response.LeaderboardResponse;
import kg.jazzlms.models.response.UserGamificationResponse;
import kg.jazzlms.utils.JsonUtils;

import java.util.Map;

import static kg.jazzlms.constants.Endpoints.*;

/** /api/gamification/** (gamification service) */
public class GamificationController extends ApiRequest {

    public GamificationController(String url) {
        super(url);
    }

    public GamificationController(String url, String token) {
        super(url, token);
    }

    // ---------- настройки ----------

    public Response getSettingsResponse() {
        return get(GAMIFICATION_SETTINGS);
    }

    public GamificationSettings getSettings() {
        return getSettingsResponse().then().statusCode(200).extract().as(GamificationSettings.class);
    }

    public Response updateSettingsResponse(GamificationSettings settings) {
        return put(GAMIFICATION_SETTINGS, JsonUtils.toJson(settings));
    }

    public Response updateSettingsRaw(String body) {
        return put(GAMIFICATION_SETTINGS, body);
    }

    public Response resetSettingsResponse() {
        return post(GAMIFICATION_SETTINGS_RESET, "");
    }

    /** ОПАСНО: обнуляет очки всех пользователей */
    public Response resetStatisticsResponse() {
        return post(GAMIFICATION_RESET_STATISTICS, "");
    }

    public Response configResponse() {
        return get(GAMIFICATION_CONFIG);
    }

    public Response rulesResponse() {
        return get(GAMIFICATION_RULES);
    }

    // ---------- пользователи ----------

    public Response meResponse() {
        return get(GAMIFICATION_ME);
    }

    public Response userResponse(Object userId) {
        return get(String.format(GAMIFICATION_USERS_ID, userId));
    }

    public UserGamificationResponse user(Object userId) {
        return userResponse(userId).then().statusCode(200).extract().as(UserGamificationResponse.class);
    }

    // ---------- рейтинг ----------

    public Response leaderboardResponse() {
        return get(GAMIFICATION_LEADERBOARD);
    }

    public Response leaderboardResponse(Map<String, ?> queryParams) {
        return get(GAMIFICATION_LEADERBOARD, queryParams);
    }

    public LeaderboardResponse leaderboard(Map<String, ?> queryParams) {
        return leaderboardResponse(queryParams).then().statusCode(200).extract().as(LeaderboardResponse.class);
    }
}
