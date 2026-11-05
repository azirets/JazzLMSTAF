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
 * Все защищённые эндпоинты user-service: без валидного токена -> 401,
 * с токеном не-админа (прямой вызов сервиса только для администраторов) -> 401.
 * Публичные: login, register, GET /api/auth/settings — здесь не проверяются.
 */
@Tag("regression")
@DisplayName("User service: доступ без токена и с токеном не-админа")
public class UserServiceSecurityTest {

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
        String path = parts[1].replace("{id}", ID).replace("{userId}", ID);
        return switch (parts[0]) {
            case "GET" -> api.get(path);
            case "POST" -> api.post(path, "{}");
            case "PUT" -> api.put(path, "{}");
            default -> api.delete(path);
        };
    }

    private static final String[] PROTECTED = {
            "GET api/users", "POST api/users", "GET api/users/{id}", "PUT api/users/{id}", "DELETE api/users/{id}",
            "GET api/users/stats", "GET api/users/export", "POST api/users/import",
            "GET api/users/{id}/groups", "GET api/users/{id}/branches",
            "GET api/groups", "POST api/groups", "GET api/groups/{id}", "PUT api/groups/{id}", "DELETE api/groups/{id}",
            "GET api/groups/{id}/users", "POST api/groups/{id}/users/{userId}", "DELETE api/groups/{id}/users/{userId}",
            "GET api/branches", "POST api/branches", "GET api/branches/{id}", "PUT api/branches/{id}", "DELETE api/branches/{id}",
            "GET api/branches/{id}/users", "POST api/branches/{id}/users/{userId}", "DELETE api/branches/{id}/users/{userId}",
            "GET api/settings", "PUT api/settings",
            "GET api/auth/me", "POST api/auth/password", "POST api/auth/impersonate/{userId}",
            "GET api/demo/status/200"
    };

    static Stream<String> protectedEndpoints() {
        return Arrays.stream(PROTECTED);
    }

    @ParameterizedTest(name = "{0} с неверным токеном -> 401")
    @MethodSource("protectedEndpoints")
    @DisplayName("Неверный токен -> 401")
    void invalidTokenIsRejected(String endpoint) {
        Response response = call(new ApiRequest(Endpoints.BASE_URL, "invalid-token"), endpoint);

        assertEquals(401, response.statusCode(), endpoint + ": " + response.asString());
    }

    @ParameterizedTest(name = "{0} с токеном LEARNER -> 401")
    @MethodSource("protectedEndpoints")
    @DisplayName("Токен не-админа -> 401")
    void learnerTokenIsRejected(String endpoint) {
        Response response = call(new ApiRequest(Endpoints.BASE_URL, learnerToken), endpoint);

        assertEquals(401, response.statusCode(), endpoint + ": " + response.asString());
    }
}
