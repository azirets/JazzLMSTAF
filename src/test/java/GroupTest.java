import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.GroupController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.GroupRequest;
import kg.jazzlms.models.response.GroupListItem;
import kg.jazzlms.models.response.GroupResponse;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static kg.jazzlms.utils.EntityManager.groupBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: группы (/api/groups)")
public class GroupTest {

    private static final GroupController GROUP_CONTROLLER = new GroupController(Endpoints.BASE_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<UUID> CREATED_GROUPS = new ArrayList<>();
    private static final List<UUID> CREATED_USERS = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private GroupResponse createGroup(GroupRequest request) {
        GroupResponse created = GROUP_CONTROLLER.createGroup(request);
        CREATED_GROUPS.add(created.getId());
        return created;
    }

    private UserResponse createUser() {
        UserResponse created = USER_CONTROLLER.createUser(EntityManager.generateUser());
        CREATED_USERS.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        CREATED_GROUPS.forEach(GROUP_CONTROLLER::deleteGroupResponse);
        CREATED_USERS.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    // ---------- POST /api/groups ----------

    @Tag("smoke")
    @Test
    @DisplayName("Только name -> 201, active = true по умолчанию")
    void createGroupWithOnlyName() {
        GroupRequest request = groupBuilder().build();

        GroupResponse created = createGroup(request);

        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertNull(created.getDescription()),
                () -> assertTrue(created.isActive()),
                () -> assertNotNull(created.getCreatedAt())
        );
    }

    @Test
    @DisplayName("Все поля заполнены -> 201, ответ совпадает с запросом")
    void createGroupWithAllFields() {
        GroupRequest request = groupBuilder().description("Описание группы").active(false).build();

        GroupResponse created = createGroup(request);

        assertAll(
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertEquals("Описание группы", created.getDescription()),
                () -> assertFalse(created.isActive())
        );
    }

    @Test
    @DisplayName("Граничные значения: name 80, description 500 -> 201")
    void createGroupWithBoundaryValues() {
        String name = "n".repeat(70) + UUID.randomUUID().toString().substring(0, 10);

        GroupResponse created = createGroup(GroupRequest.builder().name(name).description("d".repeat(500)).build());

        assertEquals(name, created.getName());
    }

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", GroupRequest.builder().build()),
                Arguments.of("name пустой", GroupRequest.builder().name("").build()),
                Arguments.of("name пробелы", GroupRequest.builder().name("   ").build()),
                Arguments.of("name 81 символ", GroupRequest.builder().name("n".repeat(81)).build()),
                Arguments.of("description 501 символ", groupBuilder().description("d".repeat(501)).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createGroupWithInvalidData(String caseName, GroupRequest request) {
        Response response = GROUP_CONTROLLER.createGroupResponse(request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Дубликат name -> 409")
    void createGroupWithDuplicateName() {
        GroupResponse first = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.createGroupResponse(groupBuilder().name(first.getName()).build());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "not a json", "{\"name\": \"abc\",", "[]", "{\"name\":\"abc\",\"active\":\"yes\"}"})
    @DisplayName("Некорректное тело -> 400")
    void createGroupWithMalformedBody(String body) {
        Response response = GROUP_CONTROLLER.createGroupRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/groups и /api/groups/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/groups/{id} -> 200, данные совпадают")
    void getGroupById() {
        GroupResponse created = createGroup(groupBuilder().description("desc").build());

        Response response = GROUP_CONTROLLER.getGroupResponse(created.getId());
        assertEquals(200, response.statusCode(), response.asString());

        GroupResponse actual = response.as(GroupResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), actual.getId()),
                () -> assertEquals(created.getName(), actual.getName()),
                () -> assertEquals("desc", actual.getDescription())
        );
    }

    @Test
    @DisplayName("Несуществующая группа -> 404")
    void getUnknownGroup() {
        Response response = GROUP_CONTROLLER.getGroupResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный id -> 400")
    void getGroupWithInvalidId(String id) {
        Response response = GROUP_CONTROLLER.getGroupResponse(id);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/groups -> 200, созданная группа в списке с members = 0")
    void getAllGroups() {
        GroupResponse created = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.getGroupsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        GroupListItem item = Arrays.stream(response.as(GroupListItem[].class))
                .filter(i -> created.getId().equals(i.getGroup().getId()))
                .findFirst().orElse(null);
        assertNotNull(item, "Созданная группа не найдена в списке");
        assertEquals(0, item.getMembers());
    }

    // ---------- PUT /api/groups/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление группы -> 200, GET показывает новые значения")
    void updateGroup() {
        GroupResponse created = createGroup(groupBuilder().build());
        GroupRequest update = groupBuilder().description("Новое описание").active(false).build();

        Response response = GROUP_CONTROLLER.updateGroupResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        GroupResponse fromGet = GROUP_CONTROLLER.getGroupResponse(created.getId()).as(GroupResponse.class);
        assertAll(
                () -> assertEquals(update.getName(), response.as(GroupResponse.class).getName()),
                () -> assertEquals(update.getName(), fromGet.getName()),
                () -> assertEquals("Новое описание", fromGet.getDescription()),
                () -> assertFalse(fromGet.isActive())
        );
    }

    @Test
    @DisplayName("Своё же имя при обновлении не считается дубликатом")
    void updateGroupWithOwnName() {
        GroupResponse created = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.updateGroupResponse(created.getId(),
                GroupRequest.builder().name(created.getName()).description("changed").build());

        assertEquals(200, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Имя другой группы при обновлении -> 409")
    void updateGroupWithDuplicateName() {
        GroupResponse first = createGroup(groupBuilder().build());
        GroupResponse second = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.updateGroupResponse(second.getId(),
                GroupRequest.builder().name(first.getName()).build());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateGroupWithInvalidData(String caseName, GroupRequest request) {
        GroupResponse created = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.updateGroupResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление несуществующей группы -> 404")
    void updateUnknownGroup() {
        Response response = GROUP_CONTROLLER.updateGroupResponse(UNKNOWN_ID, groupBuilder().build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с невалидным id -> 400")
    void updateGroupWithInvalidId() {
        Response response = GROUP_CONTROLLER.updateGroupRaw("abc", "{\"name\":\"x\"}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с некорректным телом -> 400")
    void updateGroupWithMalformedBody() {
        GroupResponse created = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.updateGroupRaw(created.getId(), "not a json");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE /api/groups/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление -> 204, дальше GET -> 404")
    void deleteGroup() {
        GroupResponse created = GROUP_CONTROLLER.createGroup(groupBuilder().build());

        Response delete = GROUP_CONTROLLER.deleteGroupResponse(created.getId());
        Response get = GROUP_CONTROLLER.getGroupResponse(created.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, get.statusCode(), get.asString())
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404")
    void deleteGroupTwice() {
        GroupResponse created = GROUP_CONTROLLER.createGroup(groupBuilder().build());
        GROUP_CONTROLLER.deleteGroupResponse(created.getId());

        Response response = GROUP_CONTROLLER.deleteGroupResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление несуществующей группы -> 404")
    void deleteUnknownGroup() {
        Response response = GROUP_CONTROLLER.deleteGroupResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление с невалидным id -> 400")
    void deleteGroupWithInvalidId() {
        Response response = GROUP_CONTROLLER.deleteGroupResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление группы с участниками -> 204, у пользователя группа пропадает")
    void deleteGroupWithMembers() {
        GroupResponse group = GROUP_CONTROLLER.createGroup(groupBuilder().build());
        UserResponse user = createUser();
        GROUP_CONTROLLER.addUserResponse(group.getId(), user.getId());

        Response delete = GROUP_CONTROLLER.deleteGroupResponse(group.getId());
        Response userGroups = USER_CONTROLLER.userGroupsResponse(user.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(0, userGroups.jsonPath().getList("$").size())
        );
    }

    // ---------- участники: POST/DELETE /api/groups/{id}/users/{userId}, GET /api/groups/{id}/users ----------

    @Tag("smoke")
    @Test
    @DisplayName("Добавление пользователя -> 204, он в списке участников и members = 1")
    void addUserToGroup() {
        GroupResponse group = createGroup(groupBuilder().build());
        UserResponse user = createUser();

        Response add = GROUP_CONTROLLER.addUserResponse(group.getId(), user.getId());
        assertEquals(204, add.statusCode(), add.asString());

        UserResponse[] members = GROUP_CONTROLLER.groupUsersResponse(group.getId()).as(UserResponse[].class);
        UserResponse[] userGroups = new UserResponse[0];
        GroupResponse[] groupsOfUser = USER_CONTROLLER.userGroupsResponse(user.getId()).as(GroupResponse[].class);
        GroupListItem item = Arrays.stream(GROUP_CONTROLLER.getGroupsResponse().as(GroupListItem[].class))
                .filter(i -> group.getId().equals(i.getGroup().getId())).findFirst().orElseThrow();

        assertAll(
                () -> assertEquals(1, members.length),
                () -> assertEquals(user.getId(), members[0].getId()),
                () -> assertEquals(1, groupsOfUser.length, "Группа не появилась у пользователя"),
                () -> assertEquals(group.getId(), groupsOfUser[0].getId()),
                () -> assertEquals(1, item.getMembers())
        );
    }

    @Test
    @DisplayName("Повторное добавление того же пользователя -> 204, дубликата нет")
    void addSameUserTwice() {
        GroupResponse group = createGroup(groupBuilder().build());
        UserResponse user = createUser();
        GROUP_CONTROLLER.addUserResponse(group.getId(), user.getId());

        Response second = GROUP_CONTROLLER.addUserResponse(group.getId(), user.getId());

        UserResponse[] members = GROUP_CONTROLLER.groupUsersResponse(group.getId()).as(UserResponse[].class);
        assertAll(
                () -> assertEquals(204, second.statusCode(), second.asString()),
                () -> assertEquals(1, members.length)
        );
    }

    @Test
    @DisplayName("Добавление в несуществующую группу -> 404")
    void addUserToUnknownGroup() {
        UserResponse user = createUser();

        Response response = GROUP_CONTROLLER.addUserResponse(UNKNOWN_ID, user.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Добавление несуществующего пользователя -> 404")
    void addUnknownUserToGroup() {
        GroupResponse group = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.addUserResponse(group.getId(), UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Добавление с невалидными id -> 400")
    void addUserWithInvalidIds() {
        GroupResponse group = createGroup(groupBuilder().build());
        UserResponse user = createUser();

        Response badGroup = GROUP_CONTROLLER.addUserResponse("abc", user.getId());
        Response badUser = GROUP_CONTROLLER.addUserResponse(group.getId(), "abc");

        assertAll(
                () -> assertEquals(400, badGroup.statusCode(), badGroup.asString()),
                () -> assertEquals(400, badUser.statusCode(), badUser.asString())
        );
    }

    @Tag("smoke")
    @Test
    @DisplayName("Удаление пользователя из группы -> 204, список участников пустой")
    void removeUserFromGroup() {
        GroupResponse group = createGroup(groupBuilder().build());
        UserResponse user = createUser();
        GROUP_CONTROLLER.addUserResponse(group.getId(), user.getId());

        Response remove = GROUP_CONTROLLER.removeUserResponse(group.getId(), user.getId());

        UserResponse[] members = GROUP_CONTROLLER.groupUsersResponse(group.getId()).as(UserResponse[].class);
        assertAll(
                () -> assertEquals(204, remove.statusCode(), remove.asString()),
                () -> assertEquals(0, members.length)
        );
    }

    @Test
    @DisplayName("Удаление пользователя, которого нет в группе -> 204")
    void removeUserWhoIsNotMember() {
        GroupResponse group = createGroup(groupBuilder().build());
        UserResponse user = createUser();

        Response response = GROUP_CONTROLLER.removeUserResponse(group.getId(), user.getId());

        assertEquals(204, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление с невалидными id -> 400")
    void removeUserWithInvalidIds() {
        Response response = GROUP_CONTROLLER.removeUserResponse("abc", "abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Участники несуществующей группы -> 404")
    void getUsersOfUnknownGroup() {
        Response response = GROUP_CONTROLLER.groupUsersResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Участники с невалидным id -> 400")
    void getUsersOfGroupWithInvalidId() {
        Response response = GROUP_CONTROLLER.groupUsersResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Новая группа без участников -> пустой список")
    void newGroupHasNoMembers() {
        GroupResponse group = createGroup(groupBuilder().build());

        Response response = GROUP_CONTROLLER.groupUsersResponse(group.getId());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("$").size())
        );
    }
}
