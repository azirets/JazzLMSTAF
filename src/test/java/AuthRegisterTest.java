import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.RegisterRequest;
import kg.jazzlms.models.response.LoginResponse;
import kg.jazzlms.models.response.PublicSettingsResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: POST /api/auth/register и GET /api/auth/settings")
public class AuthRegisterTest {

    private static final AuthController AUTH_CONTROLLER = new AuthController(Endpoints.BASE_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<UUID> CREATED = new ArrayList<>();

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    /** Валидный запрос на регистрацию с уникальными username и email */
    private static RegisterRequest.RegisterRequestBuilder registerBuilder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return RegisterRequest.builder()
                .firstName("Reg")
                .lastName("User")
                .email("reg." + suffix + "@example.com")
                .username("reg_" + suffix)
                .password("Pass123!");
    }

    private LoginResponse register(RegisterRequest request) {
        Response response = AUTH_CONTROLLER.registerResponse(request);
        assertEquals(201, response.statusCode(), response.asString());
        LoginResponse result = response.as(LoginResponse.class);
        CREATED.add(result.getUser().getId());
        return result;
    }

    // ---------- positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Регистрация -> 201, токен, пользователь LEARNER")
    void register() {
        RegisterRequest request = registerBuilder().acceptTerms(true).build();

        LoginResponse result = register(request);

        assertAll(
                () -> assertNotNull(result.getToken()),
                () -> assertNotNull(result.getUser().getId()),
                () -> assertEquals(request.getFirstName(), result.getUser().getFirstName()),
                () -> assertEquals(request.getLastName(), result.getUser().getLastName()),
                () -> assertEquals(request.getEmail(), result.getUser().getEmail()),
                () -> assertEquals(request.getUsername(), result.getUser().getUsername()),
                () -> assertEquals("LEARNER", result.getUser().getUserType()),
                () -> assertNotNull(result.getUser().getCreatedAt())
        );
    }

    @Test
    @DisplayName("Регистрация без acceptTerms -> 201 (поле необязательное)")
    void registerWithoutAcceptTerms() {
        LoginResponse result = register(registerBuilder().build());

        assertNotNull(result.getToken());
    }

    @Test
    @DisplayName("После регистрации можно войти с тем же паролем")
    void canLogInAfterRegister() {
        RegisterRequest request = registerBuilder().build();
        register(request);

        Response login = AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword());

        assertEquals(200, login.statusCode(), login.asString());
    }

    @Test
    @DisplayName("Токен после регистрации работает в /api/auth/me только у админа, но не ломает сервис (401 для LEARNER напрямую)")
    void registeredLearnerCannotCallServiceDirectly() {
        LoginResponse result = register(registerBuilder().build());

        Response me = AUTH_CONTROLLER.meResponse(result.getToken());

        assertEquals(401, me.statusCode(), me.asString());
    }

    @Test
    @DisplayName("Граничные значения: username 3 и 50, firstName 50, password 6 и 72 -> 201")
    void registerWithBoundaryValues() {
        register(registerBuilder().username("u" + UUID.randomUUID().toString().substring(0, 2)).password("123456").build());
        register(registerBuilder().username("x".repeat(40) + UUID.randomUUID().toString().substring(0, 10))
                .firstName("a".repeat(50)).lastName("b".repeat(50)).password("p".repeat(72)).build());
    }

    // ---------- negative ----------

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("firstName = null", registerBuilder().firstName(null).build()),
                Arguments.of("firstName пустой", registerBuilder().firstName("").build()),
                Arguments.of("lastName = null", registerBuilder().lastName(null).build()),
                Arguments.of("lastName пустой", registerBuilder().lastName("").build()),
                Arguments.of("email = null", registerBuilder().email(null).build()),
                Arguments.of("email пустой", registerBuilder().email("").build()),
                Arguments.of("email без @", registerBuilder().email("testexample.com").build()),
                Arguments.of("email без домена", registerBuilder().email("test@").build()),
                Arguments.of("username = null", registerBuilder().username(null).build()),
                Arguments.of("username 2 символа", registerBuilder().username("ab").build()),
                Arguments.of("username 51 символ", registerBuilder().username("u".repeat(51)).build()),
                Arguments.of("password = null", registerBuilder().password(null).build()),
                Arguments.of("password пустой", registerBuilder().password("").build()),
                Arguments.of("password 5 символов", registerBuilder().password("12345").build()),
                Arguments.of("password 73 символа", registerBuilder().password("p".repeat(73)).build()),
                Arguments.of("firstName 51 символ", registerBuilder().firstName("a".repeat(51)).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void registerWithInvalidData(String caseName, RegisterRequest request) {
        Response response = AUTH_CONTROLLER.registerResponse(request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Дубликат email -> 409")
    void registerWithDuplicateEmail() {
        RegisterRequest first = registerBuilder().build();
        register(first);

        Response response = AUTH_CONTROLLER.registerResponse(registerBuilder().email(first.getEmail()).build());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Дубликат username -> 409")
    void registerWithDuplicateUsername() {
        RegisterRequest first = registerBuilder().build();
        register(first);

        Response response = AUTH_CONTROLLER.registerResponse(registerBuilder().username(first.getUsername()).build());

        assertEquals(409, response.statusCode(), response.asString());
    }

    // ---------- GET /api/auth/settings ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/auth/settings -> 200 без токена, публичные поля")
    void publicSettings() {
        Response response = AUTH_CONTROLLER.publicSettingsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        PublicSettingsResponse settings = response.as(PublicSettingsResponse.class);
        assertAll(
                () -> assertNotNull(settings.getSiteName()),
                () -> assertNotNull(settings.getVisibleUserFormat()),
                () -> assertTrue(List.of("FIRST_LAST", "LAST_FIRST", "USERNAME").contains(settings.getVisibleUserFormat()))
        );
    }

    @Test
    @DisplayName("Публичные настройки не раскрывают служебные поля")
    void publicSettingsHaveNoInternalFields() {
        String body = AUTH_CONTROLLER.publicSettingsResponse().asString();

        assertAll(
                () -> assertFalse(body.contains("lockAfterAttempts")),
                () -> assertFalse(body.contains("passwordMaxAgeDays")),
                () -> assertFalse(body.contains("defaultGroupId"))
        );
    }
}
