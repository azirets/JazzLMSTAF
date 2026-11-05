import io.restassured.response.Response;
import kg.jazzlms.config.ConfigReader;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.LoginResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: POST /api/auth/login и GET /api/auth/me")
public class AuthLoginTest {

    private static final AuthController AUTH_CONTROLLER = new AuthController(Endpoints.BASE_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<UUID> CREATED = new ArrayList<>();

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    private UserRequest createLoginableUser() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        CREATED.add(USER_CONTROLLER.createUser(request).getId());
        return request;
    }

    // ---------- login: positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Логин админа -> 200, токен и данные пользователя")
    void adminLogin() {
        String username = ConfigReader.getValue("admin.username");

        Response response = AUTH_CONTROLLER.loginResponse(username, ConfigReader.getValue("admin.password"));
        assertEquals(200, response.statusCode(), response.asString());

        LoginResponse login = response.as(LoginResponse.class);
        assertAll(
                () -> assertNotNull(login.getToken()),
                () -> assertFalse(login.getToken().isBlank()),
                () -> assertNotNull(login.getUser()),
                () -> assertEquals(username, login.getUser().getUsername()),
                () -> assertEquals("SUPER_ADMIN", login.getUser().getUserType()),
                () -> assertFalse(login.isMustChangePassword())
        );
    }

    @Test
    @DisplayName("Логин созданного пользователя -> 200, lastLoginAt проставляется")
    void createdUserLogin() {
        UserRequest request = createLoginableUser();

        Response response = AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword());
        assertEquals(200, response.statusCode(), response.asString());

        LoginResponse login = response.as(LoginResponse.class);
        assertAll(
                () -> assertEquals(request.getUsername(), login.getUser().getUsername()),
                () -> assertEquals(request.getEmail(), login.getUser().getEmail()),
                () -> assertNotNull(login.getUser().getLastLoginAt(), "lastLoginAt должен проставиться после входа")
        );
    }

    @Test
    @DisplayName("В ответе логина нет пароля")
    void loginResponseHasNoPassword() {
        UserRequest request = createLoginableUser();

        String body = AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword()).asString();

        assertFalse(body.toLowerCase().contains("password\":\"") , "В ответе не должно быть пароля");
    }

    // ---------- login: negative ----------

    @Test
    @DisplayName("Неверный пароль -> 401")
    void wrongPassword() {
        UserRequest request = createLoginableUser();

        Response response = AUTH_CONTROLLER.loginResponse(request.getUsername(), "WrongPass999!");

        assertEquals(401, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий пользователь -> 401")
    void unknownUser() {
        Response response = AUTH_CONTROLLER.loginResponse("no_such_user_" + UUID.randomUUID(), "Pass123!");

        assertEquals(401, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Неактивный пользователь -> 401")
    void inactiveUser() {
        UserRequest request = EntityManager.loginableUserBuilder().active(false).build();
        CREATED.add(USER_CONTROLLER.createUser(request).getId());

        Response response = AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword());

        assertEquals(401, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Пароль другого регистра -> 401 (пароль чувствителен к регистру)")
    void passwordIsCaseSensitive() {
        UserRequest request = createLoginableUser();

        Response response = AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword().toUpperCase());

        assertEquals(401, response.statusCode(), response.asString());
    }

    static Stream<Arguments> blankCredentials() {
        return Stream.of(
                Arguments.of("username пустой", "", "Pass123!"),
                Arguments.of("password пустой", "admin", ""),
                Arguments.of("оба пустые", "", ""),
                Arguments.of("username пробелы", "   ", "Pass123!"),
                Arguments.of("password пробелы", "admin", "   ")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blankCredentials")
    @DisplayName("Пустые или пробельные поля -> 400")
    void blankCredentialsAreRejected(String caseName, String username, String password) {
        Response response = AUTH_CONTROLLER.loginResponse(username, password);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {
            "",
            "{}",
            "not a json",
            "{\"username\": \"admin\",",
            "[]",
            "{\"username\":\"admin\"}",
            "{\"password\":\"admin123\"}"
    })
    @DisplayName("Некорректное тело -> 400")
    void malformedBody(String body) {
        Response response = AUTH_CONTROLLER.loginRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/auth/me ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/auth/me с токеном админа -> 200, это админ")
    void meReturnsCurrentUser() {
        LoginResponse login = AUTH_CONTROLLER.login(ConfigReader.getValue("admin.username"), ConfigReader.getValue("admin.password"));

        Response response = AUTH_CONTROLLER.meResponse(login.getToken());
        assertEquals(200, response.statusCode(), response.asString());

        UserResponse me = response.as(UserResponse.class);
        assertAll(
                () -> assertEquals(login.getUser().getId(), me.getId()),
                () -> assertEquals(login.getUser().getUsername(), me.getUsername()),
                () -> assertEquals("SUPER_ADMIN", me.getUserType())
        );
    }

    @Test
    @DisplayName("GET /api/auth/me с токеном созданного ADMIN -> возвращает его самого")
    void meForCreatedAdmin() {
        UserRequest request = EntityManager.loginableUserBuilder().userType("ADMIN").build();
        UserResponse created = USER_CONTROLLER.createUser(request);
        CREATED.add(created.getId());
        String token = AUTH_CONTROLLER.login(request.getUsername(), request.getPassword()).getToken();

        Response response = AUTH_CONTROLLER.meResponse(token);
        assertEquals(200, response.statusCode(), response.asString());

        UserResponse me = response.as(UserResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), me.getId()),
                () -> assertEquals(request.getUsername(), me.getUsername()),
                () -> assertEquals("ADMIN", me.getUserType())
        );
    }

    @ParameterizedTest(name = "token = ''{0}'' -> 401")
    @ValueSource(strings = {"invalid-token", "abc.def.ghi", "null"})
    @DisplayName("GET /api/auth/me с неверным токеном -> 401")
    void meWithInvalidToken(String token) {
        Response response = AUTH_CONTROLLER.meResponse(token);

        assertEquals(401, response.statusCode(), response.asString());
    }
}
