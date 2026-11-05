import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: PUT /api/users/{id}")
public class UserUpdateTest {

    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final AuthController AUTH_CONTROLLER = new AuthController(Endpoints.BASE_URL);
    private static final List<UUID> CREATED = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private UserResponse createUser(UserRequest request) {
        UserResponse created = USER_CONTROLLER.createUser(request);
        CREATED.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    // ---------- positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление всех полей -> 200, ответ и GET показывают новые значения")
    void updateAllFields() {
        UserResponse created = createUser(EntityManager.generateUser());
        String newEmail = "new." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        Map<String, Object> fields = Map.of(
                "firstName", "Новое",
                "lastName", "Имя",
                "email", newEmail,
                "userType", "TRAINER",
                "timeZone", "Asia/Bishkek",
                "language", "ru",
                "bio", "Новое bio",
                "active", true,
                "excludeFromEmails", true);

        Response response = USER_CONTROLLER.updateUserResponse(created.getId(), fields);
        assertEquals(200, response.statusCode(), response.asString());

        UserResponse fromGet = USER_CONTROLLER.getUserByIdResponse(created.getId()).as(UserResponse.class);
        for (UserResponse actual : List.of(response.as(UserResponse.class), fromGet)) {
            assertAll(
                    () -> assertEquals(created.getId(), actual.getId()),
                    () -> assertEquals("Новое", actual.getFirstName()),
                    () -> assertEquals("Имя", actual.getLastName()),
                    () -> assertEquals(newEmail, actual.getEmail()),
                    () -> assertEquals(created.getUsername(), actual.getUsername(), "username не меняется"),
                    () -> assertEquals("TRAINER", actual.getUserType()),
                    () -> assertEquals("Asia/Bishkek", actual.getTimeZone()),
                    () -> assertEquals("ru", actual.getLanguage()),
                    () -> assertEquals("Новое bio", actual.getBio()),
                    () -> assertTrue(actual.isActive()),
                    () -> assertTrue(actual.isExcludeFromEmails())
            );
        }
    }

    @Test
    @DisplayName("Частичное обновление меняет только переданное поле")
    void partialUpdate() {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserResponse(created.getId(), Map.of("bio", "Только bio"));
        assertEquals(200, response.statusCode(), response.asString());

        UserResponse actual = response.as(UserResponse.class);
        assertAll(
                () -> assertEquals("Только bio", actual.getBio()),
                () -> assertEquals(created.getFirstName(), actual.getFirstName()),
                () -> assertEquals(created.getLastName(), actual.getLastName()),
                () -> assertEquals(created.getEmail(), actual.getEmail()),
                () -> assertEquals(created.getUserType(), actual.getUserType())
        );
    }

    @Test
    @DisplayName("Пустое тело {} -> 200, ничего не меняется")
    void updateWithEmptyObject() {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserResponse(created.getId(), Map.of());
        assertEquals(200, response.statusCode(), response.asString());

        UserResponse actual = response.as(UserResponse.class);
        assertAll(
                () -> assertEquals(created.getFirstName(), actual.getFirstName()),
                () -> assertEquals(created.getEmail(), actual.getEmail()),
                () -> assertEquals(created.getUserType(), actual.getUserType())
        );
    }

    @Test
    @DisplayName("Свой же email в запросе не считается дубликатом")
    void updateWithOwnEmail() {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserResponse(created.getId(), Map.of("email", created.getEmail()));

        assertEquals(200, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Смена пароля через PUT: входит с новым паролем, со старым нет")
    void updatePasswordChangesLogin() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse created = createUser(request);

        Response update = USER_CONTROLLER.updateUserResponse(created.getId(), Map.of("password", "NewPass456!"));
        assertEquals(200, update.statusCode(), update.asString());

        assertAll(
                () -> assertEquals(200, AUTH_CONTROLLER.loginResponse(request.getUsername(), "NewPass456!").statusCode()),
                () -> assertEquals(401, AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword()).statusCode())
        );
    }

    @Test
    @DisplayName("active=false -> пользователь не может войти, active=true -> может")
    void updateActiveFlagControlsLogin() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse created = createUser(request);

        USER_CONTROLLER.updateUserResponse(created.getId(), Map.of("active", false));
        int statusWhenInactive = AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword()).statusCode();

        USER_CONTROLLER.updateUserResponse(created.getId(), Map.of("active", true));
        int statusWhenActive = AUTH_CONTROLLER.loginResponse(request.getUsername(), request.getPassword()).statusCode();

        assertAll(
                () -> assertEquals(401, statusWhenInactive),
                () -> assertEquals(200, statusWhenActive)
        );
    }

    @ParameterizedTest(name = "userType = {0}")
    @ValueSource(strings = {"SUPER_ADMIN", "ADMIN", "TRAINER", "LEARNER"})
    @DisplayName("Смена роли на каждую из допустимых")
    void updateEachUserType(String userType) {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserResponse(created.getId(), Map.of("userType", userType));

        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(userType, response.as(UserResponse.class).getUserType());
    }

    @Test
    @DisplayName("Граничные значения: firstName 50, bio 800, password 72 -> 200")
    void updateWithBoundaryValues() {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserResponse(created.getId(), Map.of(
                "firstName", "a".repeat(50),
                "lastName", "b".repeat(50),
                "bio", "c".repeat(800),
                "password", "p".repeat(72)));

        assertEquals(200, response.statusCode(), response.asString());
    }

    // ---------- negative ----------

    static List<Arguments> invalidFields() {
        return List.of(
                Arguments.of("email без @", Map.<String, Object>of("email", "testexample.com")),
                // ПАДАЕТ (баг бэка): 200 вместо 400 — пустой email принимается и затирает email пользователя
                Arguments.of("email пустой",Map.<String, Object>of("email", "")),
                Arguments.of("firstName 51 символ", Map.<String, Object>of("firstName", "a".repeat(51))),
                Arguments.of("lastName 51 символ", Map.<String, Object>of("lastName", "a".repeat(51))),
                Arguments.of("bio 801 символ", Map.<String, Object>of("bio", "a".repeat(801))),
                Arguments.of("password 5 символов", Map.<String, Object>of("password", "12345")),
                Arguments.of("password 73 символа", Map.<String, Object>of("password", "p".repeat(73))),
                // ПАДАЕТ (баг бэка): 200 вместо 400 — timeZone не валидируется, как и при создании
                Arguments.of("timeZone невалидный", Map.<String, Object>of("timeZone", "Mars/Olympus")),
                // ПАДАЕТ (баг бэка): 200 вместо 400 — language не валидируется, как и при создании
                Arguments.of("language невалидный", Map.<String, Object>of("language", "xx"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFields")
    @DisplayName("Невалидные данные -> 400")
    void updateWithInvalidData(String caseName, Map<String, Object> fields) {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserResponse(created.getId(), fields);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Email другого пользователя -> 409")
    void updateWithDuplicateEmail() {
        UserResponse first = createUser(EntityManager.generateUser());
        UserResponse second = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserResponse(second.getId(), Map.of("email", first.getEmail()));

        assertEquals(409, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий id -> 404")
    void updateUnknownUser() {
        Response response = USER_CONTROLLER.updateUserResponse(UNKNOWN_ID, Map.of("bio", "x"));

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный id -> 400")
    void updateWithInvalidId(String id) {
        Response response = USER_CONTROLLER.updateUserRaw(id, "{\"bio\":\"x\"}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {
            "",
            "not a json",
            "{\"firstName\": \"Casey\",",
            "[]",
            "{\"userType\":\"INVALID_ROLE\"}",
            "{\"active\":\"yes\"}"
    })
    @DisplayName("Некорректное тело -> 400")
    void updateWithMalformedBody(String body) {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.updateUserRaw(created.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }
}
