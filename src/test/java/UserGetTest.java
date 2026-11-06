import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.models.response.UserStatsResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: чтение пользователей (список, по id, поиск, статистика)")
public class UserGetTest {

    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
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

    // ---------- GET /api/users/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/users/{id} -> 200, данные совпадают с созданными")
    void getUserById() {
        UserRequest request = EntityManager.userBuilder().bio("О себе").userType("TRAINER").build();
        UserResponse created = createUser(request);

        Response response = USER_CONTROLLER.getUserByIdResponse(created.getId());
        assertEquals(200, response.statusCode(), response.asString());

        UserResponse actual = response.as(UserResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), actual.getId()),
                () -> assertEquals(request.getFirstName(), actual.getFirstName()),
                () -> assertEquals(request.getLastName(), actual.getLastName()),
                () -> assertEquals(request.getEmail(), actual.getEmail()),
                () -> assertEquals(request.getUsername(), actual.getUsername()),
                () -> assertEquals("TRAINER", actual.getUserType()),
                () -> assertEquals("О себе", actual.getBio())
        );
    }

    @Test
    @DisplayName("В ответе нет пароля")
    void responseHasNoPassword() {
        UserResponse created = createUser(EntityManager.loginableUserBuilder().build());

        String body = USER_CONTROLLER.getUserByIdResponse(created.getId()).asString();

        assertFalse(body.toLowerCase().contains("password"), "В ответе не должно быть пароля: " + body);
    }

    @Test
    @DisplayName("Несуществующий id -> 404")
    void getUnknownUser() {
        Response response = USER_CONTROLLER.getUserByIdResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123", "not-a-uuid"})
    @DisplayName("Невалидный id -> 400")
    void getUserWithInvalidId(String id) {
        Response response = USER_CONTROLLER.getUserByIdResponse(id);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/users ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/users -> 200, созданный пользователь есть в списке")
    void getAllUsersContainsCreated() {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.getUsersResponse();
        assertEquals(200, response.statusCode(), response.asString());

        List<UserResponse> users = Arrays.asList(response.as(UserResponse[].class));
        assertFalse(users.isEmpty(), "Список пользователей пустой");
        assertTrue(users.stream().anyMatch(u -> created.getId().equals(u.getId())),
                "Созданный пользователь не найден в списке");
    }

    @Test
    @DisplayName("search по username находит пользователя")
    void searchByUsername() {
        UserResponse created = createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.getUsersResponse(Map.of("search", created.getUsername()));
        assertEquals(200, response.statusCode(), response.asString());

        List<UserResponse> users = Arrays.asList(response.as(UserResponse[].class));
        assertEquals(1, users.size(), "Ожидали ровно одного пользователя: " + response.asString());
        assertEquals(created.getId(), users.get(0).getId());
    }

    @Test
    @DisplayName("search по email находит пользователя")
    void searchByEmail() {
        UserResponse created = createUser(EntityManager.generateUser());

        List<UserResponse> users = Arrays.asList(USER_CONTROLLER
                .getUsersResponse(Map.of("search", created.getEmail())).as(UserResponse[].class));

        assertTrue(users.stream().anyMatch(u -> created.getId().equals(u.getId())),
                "Пользователь не найден по email");
    }

    @Test
    @DisplayName("search без совпадений -> 200 и пустой список")
    void searchWithoutMatches() {
        Response response = USER_CONTROLLER.getUsersResponse(Map.of("search", "no-such-user-" + UUID.randomUUID()));

        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(0, response.as(UserResponse[].class).length);
    }

    @Test
    @DisplayName("active=false возвращает только неактивных, active=true только активных")
    void filterByActive() {
        UserResponse inactive = createUser(EntityManager.userBuilder().active(false).build());
        UserResponse active = createUser(EntityManager.userBuilder().active(true).build());

        List<UserResponse> inactiveList = Arrays.asList(
                USER_CONTROLLER.getUsersResponse(Map.of("active", false)).as(UserResponse[].class));
        List<UserResponse> activeList = Arrays.asList(
                USER_CONTROLLER.getUsersResponse(Map.of("active", true)).as(UserResponse[].class));

        assertAll(
                () -> assertTrue(inactiveList.stream().anyMatch(u -> inactive.getId().equals(u.getId()))),
                () -> assertTrue(inactiveList.stream().noneMatch(UserResponse::isActive)),
                () -> assertTrue(activeList.stream().anyMatch(u -> active.getId().equals(u.getId()))),
                () -> assertTrue(activeList.stream().allMatch(UserResponse::isActive))
        );
    }

    @Test
    @DisplayName("active с неверным значением -> 400")
    void filterByInvalidActive() {
        Response response = USER_CONTROLLER.getUsersResponse(Map.of("active", "maybe"));

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/users/stats ----------

    @Test
    @DisplayName("GET /api/users/stats -> 200, total >= active")
    void getStats() {
        Response response = USER_CONTROLLER.statsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        UserStatsResponse stats = response.as(UserStatsResponse.class);
        assertAll(
                () -> assertTrue(stats.getTotal() > 0, "total должен быть больше 0"),
                () -> assertTrue(stats.getActive() >= 0),
                () -> assertTrue(stats.getTotal() >= stats.getActive(), "total не может быть меньше active")
        );
    }

    // ---------- GET /api/users/{id}/groups и /branches ----------

    @Test
    @DisplayName("У нового пользователя нет групп и филиалов")
    void newUserHasNoGroupsAndBranches() {
        UserResponse created = createUser(EntityManager.generateUser());

        Response groups = USER_CONTROLLER.userGroupsResponse(created.getId());
        Response branches = USER_CONTROLLER.userBranchesResponse(created.getId());

        assertAll(
                () -> assertEquals(200, groups.statusCode(), groups.asString()),
                () -> assertEquals(0, groups.jsonPath().getList("$").size()),
                () -> assertEquals(200, branches.statusCode(), branches.asString()),
                () -> assertEquals(0, branches.jsonPath().getList("$").size())
        );
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный id в /groups и /branches -> 400")
    void userGroupsAndBranchesWithInvalidId(String id) {
        Response groups = USER_CONTROLLER.userGroupsResponse(id);
        Response branches = USER_CONTROLLER.userBranchesResponse(id);

        assertAll(
                () -> assertEquals(400, groups.statusCode(), groups.asString()),
                () -> assertEquals(400, branches.statusCode(), branches.asString())
        );
    }
}
