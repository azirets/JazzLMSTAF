import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Все защищённые эндпоинты Gamification service: неверный токен -> 401,
 * токен не-админа при прямом вызове сервиса (мимо gateway) -> 401.
 * Опасные эндпоинты (стирающие данные всего окружения) в проверку не включены.
 */
@Tag("regression")
@DisplayName("Gamification service: доступ с неверным токеном и с токеном не-админа")
public class GamificationServiceSecurityTest {

    private static final UserController ADMIN_USERS = new UserController(Endpoints.BASE_URL);
    private static final String ID = "00000000-0000-0000-0000-000000000000";
    private static UUID learnerId;
    private static String learnerToken;

    @BeforeAll
    static void createLearner() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse learner = ADMIN_USERS.createUser(request);
        learnerId = learner.getId();
        learnerToken = new AuthController(Endpoints.BASE_URL).login(request.getUsername(), request.getPassword()).getToken();
    }

    @AfterAll
    static void cleanUp() {
        ADMIN_USERS.deleteUserResponse(learnerId);
    }

    /** "METHOD путь" без ведущего слеша, как в Endpoints */
    private static Response call(ApiRequest api, String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        String path = parts[1].replace("{id}", ID);
        return switch (parts[0]) {
            case "GET" -> api.get(path);
            case "POST" -> api.post(path, "{}");
            case "PUT" -> api.put(path, "{}");
            default -> api.delete(path);
        };
    }

    private static final String[] ENDPOINTS = {
            "GET api/gamification/settings",
            "PUT api/gamification/settings",
            "POST api/gamification/settings/reset",
            "GET api/gamification/config",
            "GET api/gamification/rules",
            "GET api/gamification/me",
            "GET api/gamification/users/{id}",
            "GET api/gamification/leaderboard"
    };

    static Stream<String> protectedEndpoints() {
        return Arrays.stream(ENDPOINTS);
    }

    @ParameterizedTest(name = "{0} с неверным токеном -> 401")
    @MethodSource("protectedEndpoints")
    @DisplayName("Неверный токен -> 401")
    void invalidTokenIsRejected(String endpoint) {
        Response response = call(new ApiRequest(Endpoints.GAMIFICATION_URL, "invalid-token"), endpoint);

        assertEquals(401, response.statusCode(), endpoint + ": " + response.asString());
    }

    @ParameterizedTest(name = "{0} с токеном LEARNER -> 401")
    @MethodSource("protectedEndpoints")
    @DisplayName("Токен не-админа -> 401")
    void learnerTokenIsRejected(String endpoint) {
        Response response = call(new ApiRequest(Endpoints.GAMIFICATION_URL, learnerToken), endpoint);

        assertEquals(401, response.statusCode(), endpoint + ": " + response.asString());
    }
}
