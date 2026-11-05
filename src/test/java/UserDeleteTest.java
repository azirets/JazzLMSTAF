import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.GroupController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.GroupResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: DELETE /api/users/{id}")
public class UserDeleteTest {

    private final UserController userController = new UserController(Endpoints.BASE_URL);
    private final GroupController groupController = new GroupController(Endpoints.BASE_URL);
    private final AuthController authController = new AuthController(Endpoints.BASE_URL);
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Tag("smoke")
    @Test
    @DisplayName("Удаление -> 204, дальше GET по id -> 404")
    void deleteUser() {
        UserResponse created = userController.createUser(EntityManager.generateUser());

        Response delete = userController.deleteUserResponse(created.getId());
        Response get = userController.getUserByIdResponse(created.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, get.statusCode(), get.asString())
        );
    }

    @Test
    @DisplayName("Удалённого пользователя нет в списке")
    void deletedUserIsNotInList() {
        UserResponse created = userController.createUser(EntityManager.generateUser());

        userController.deleteUserResponse(created.getId());

        UserResponse[] users = userController.getUsersResponse().as(UserResponse[].class);
        assertTrue(Arrays.stream(users).noneMatch(u -> created.getId().equals(u.getId())));
    }

    @Test
    @DisplayName("Удалённый пользователь не может войти -> 401")
    void deletedUserCannotLogIn() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse created = userController.createUser(request);

        userController.deleteUserResponse(created.getId());

        Response login = authController.loginResponse(request.getUsername(), request.getPassword());
        assertEquals(401, login.statusCode(), login.asString());
    }

    @Test
    @DisplayName("Пользователь, состоящий в группе, удаляется, из группы пропадает")
    void deleteUserThatIsInGroup() {
        UserResponse user = userController.createUser(EntityManager.generateUser());
        GroupResponse group = groupController.createGroup(EntityManager.groupBuilder().build());
        groupController.addUserResponse(group.getId(), user.getId());

        Response delete = userController.deleteUserResponse(user.getId());
        Response members = groupController.groupUsersResponse(group.getId());
        groupController.deleteGroupResponse(group.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(0, members.jsonPath().getList("$").size(), "Пользователь остался в группе")
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404")
    void deleteUserTwice() {
        UserResponse created = userController.createUser(EntityManager.generateUser());
        userController.deleteUserResponse(created.getId());

        Response response = userController.deleteUserResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий id -> 404")
    void deleteUnknownUser() {
        Response response = userController.deleteUserResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный id -> 400")
    void deleteWithInvalidId(String id) {
        Response response = userController.deleteUserResponse(id);

        assertEquals(400, response.statusCode(), response.asString());
    }
}
