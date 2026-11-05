import io.restassured.response.Response;
import kg.jazzlms.config.ConfigReader;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.ChangePasswordRequest;
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

/**
 * Прямые вызовы user-service разрешены только администраторам,
 * поэтому для смены пароля создаём отдельного пользователя с ролью ADMIN
 * (пароль основного админа из app.properties не трогаем).
 */
@Tag("regression")
@DisplayName("User service: POST /api/auth/password и /api/auth/impersonate/{userId}")
public class AuthPasswordImpersonateTest {

    private static final AuthController AUTH_CONTROLLER = new AuthController(Endpoints.BASE_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<UUID> CREATED = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String NEW_PASSWORD = "NewPass456!";

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    private UserRequest createAdmin() {
        UserRequest request = EntityManager.loginableUserBuilder().userType("ADMIN").build();
        CREATED.add(USER_CONTROLLER.createUser(request).getId());
        return request;
    }

    private String adminToken() {
        return AUTH_CONTROLLER.login(ConfigReader.getValue("admin.username"), ConfigReader.getValue("admin.password")).getToken();
    }

    // ---------- POST /api/auth/password ----------

    @Tag("smoke")
    @Test
    @DisplayName("Смена пароля -> 204, новый пароль работает, старый нет")
    void changePassword() {
        UserRequest admin = createAdmin();
        String token = AUTH_CONTROLLER.login(admin.getUsername(), admin.getPassword()).getToken();

        Response response = AUTH_CONTROLLER.changePasswordResponse(token,
                ChangePasswordRequest.builder().currentPassword(admin.getPassword()).newPassword(NEW_PASSWORD).build());
        assertEquals(204, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals(200, AUTH_CONTROLLER.loginResponse(admin.getUsername(), NEW_PASSWORD).statusCode()),
                () -> assertEquals(401, AUTH_CONTROLLER.loginResponse(admin.getUsername(), admin.getPassword()).statusCode())
        );
    }

    @Test
    @DisplayName("Граничная длина нового пароля: 6 и 72 символа -> 204")
    void changePasswordBoundaryLength() {
        UserRequest admin = createAdmin();
        String token = AUTH_CONTROLLER.login(admin.getUsername(), admin.getPassword()).getToken();

        Response toShort = AUTH_CONTROLLER.changePasswordResponse(token,
                ChangePasswordRequest.builder().currentPassword(admin.getPassword()).newPassword("123456").build());
        Response toLong = AUTH_CONTROLLER.changePasswordResponse(token,
                ChangePasswordRequest.builder().currentPassword("123456").newPassword("p".repeat(72)).build());

        assertAll(
                () -> assertEquals(204, toShort.statusCode(), toShort.asString()),
                () -> assertEquals(204, toLong.statusCode(), toLong.asString())
        );
    }

    @Test
    @DisplayName("Неверный текущий пароль -> 401, пароль не меняется")
    void changePasswordWithWrongCurrent() {
        UserRequest admin = createAdmin();
        String token = AUTH_CONTROLLER.login(admin.getUsername(), admin.getPassword()).getToken();

        Response response = AUTH_CONTROLLER.changePasswordResponse(token,
                ChangePasswordRequest.builder().currentPassword("WrongPass999!").newPassword(NEW_PASSWORD).build());

        assertAll(
                () -> assertEquals(401, response.statusCode(), response.asString()),
                () -> assertEquals(200, AUTH_CONTROLLER.loginResponse(admin.getUsername(), admin.getPassword()).statusCode())
        );
    }

    static Stream<Arguments> invalidPasswordRequests() {
        return Stream.of(
                Arguments.of("новый пароль 5 символов", ChangePasswordRequest.builder().currentPassword("Pass123!").newPassword("12345").build()),
                Arguments.of("новый пароль 73 символа", ChangePasswordRequest.builder().currentPassword("Pass123!").newPassword("p".repeat(73)).build()),
                Arguments.of("новый пароль пустой", ChangePasswordRequest.builder().currentPassword("Pass123!").newPassword("").build()),
                Arguments.of("новый пароль = null", ChangePasswordRequest.builder().currentPassword("Pass123!").build()),
                Arguments.of("текущий пароль пустой", ChangePasswordRequest.builder().currentPassword("").newPassword(NEW_PASSWORD).build()),
                Arguments.of("текущий пароль = null", ChangePasswordRequest.builder().newPassword(NEW_PASSWORD).build()),
                Arguments.of("пустой объект", ChangePasswordRequest.builder().build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPasswordRequests")
    @DisplayName("Невалидный запрос смены пароля -> 400")
    void changePasswordWithInvalidData(String caseName, ChangePasswordRequest request) {
        UserRequest admin = createAdmin();
        String token = AUTH_CONTROLLER.login(admin.getUsername(), admin.getPassword()).getToken();

        Response response = AUTH_CONTROLLER.changePasswordResponse(token, request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Смена пароля с неверным токеном -> 401")
    void changePasswordWithInvalidToken() {
        Response response = AUTH_CONTROLLER.changePasswordResponse("invalid-token",
                ChangePasswordRequest.builder().currentPassword("Pass123!").newPassword(NEW_PASSWORD).build());

        assertEquals(401, response.statusCode(), response.asString());
    }

    // ---------- POST /api/auth/impersonate/{userId} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Админ входит от имени пользователя -> 200, токен и данные этого пользователя")
    void impersonate() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse learner = USER_CONTROLLER.createUser(request);
        CREATED.add(learner.getId());

        Response response = AUTH_CONTROLLER.impersonateResponse(adminToken(), learner.getId());
        assertEquals(200, response.statusCode(), response.asString());

        LoginResponse result = response.as(LoginResponse.class);
        assertAll(
                () -> assertNotNull(result.getToken()),
                () -> assertEquals(learner.getId(), result.getUser().getId()),
                () -> assertEquals(request.getUsername(), result.getUser().getUsername()),
                () -> assertEquals("LEARNER", result.getUser().getUserType())
        );
    }

    @Test
    @DisplayName("Токен имперсонации отличается от токена админа")
    void impersonationTokenIsDifferent() {
        UserResponse learner = USER_CONTROLLER.createUser(EntityManager.generateUser());
        CREATED.add(learner.getId());
        String adminToken = adminToken();

        LoginResponse result = AUTH_CONTROLLER.impersonateResponse(adminToken, learner.getId()).as(LoginResponse.class);

        assertNotEquals(adminToken, result.getToken());
    }

    @Test
    @DisplayName("Имперсонация несуществующего пользователя -> 404")
    void impersonateUnknownUser() {
        Response response = AUTH_CONTROLLER.impersonateResponse(adminToken(), UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "userId = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный userId -> 400")
    void impersonateWithInvalidId(String id) {
        Response response = AUTH_CONTROLLER.impersonateResponse(adminToken(), id);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Имперсонация с неверным токеном -> 401")
    void impersonateWithInvalidToken() {
        UserResponse learner = USER_CONTROLLER.createUser(EntityManager.generateUser());
        CREATED.add(learner.getId());

        Response response = AUTH_CONTROLLER.impersonateResponse("invalid-token", learner.getId());

        assertEquals(401, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Имперсонация с токеном обычного пользователя -> 401")
    void impersonateWithLearnerToken() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse learner = USER_CONTROLLER.createUser(request);
        CREATED.add(learner.getId());
        String learnerToken = AUTH_CONTROLLER.login(request.getUsername(), request.getPassword()).getToken();

        Response response = AUTH_CONTROLLER.impersonateResponse(learnerToken, learner.getId());

        assertEquals(401, response.statusCode(), response.asString());
    }
}
