import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.BranchController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.BranchRequest;
import kg.jazzlms.models.response.BranchListItem;
import kg.jazzlms.models.response.BranchResponse;
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

import static kg.jazzlms.utils.EntityManager.branchBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: филиалы (/api/branches)")
public class BranchTest {

    private static final BranchController BRANCH_CONTROLLER = new BranchController(Endpoints.BASE_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<UUID> CREATED_BRANCHES = new ArrayList<>();
    private static final List<UUID> CREATED_USERS = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private BranchResponse createBranch(BranchRequest request) {
        BranchResponse created = BRANCH_CONTROLLER.createBranch(request);
        CREATED_BRANCHES.add(created.getId());
        return created;
    }

    private UserResponse createUser() {
        UserResponse created = USER_CONTROLLER.createUser(EntityManager.generateUser());
        CREATED_USERS.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        CREATED_BRANCHES.forEach(BRANCH_CONTROLLER::deleteBranchResponse);
        CREATED_USERS.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    // ---------- POST /api/branches ----------

    @Tag("smoke")
    @Test
    @DisplayName("Только name -> 201, сервер проставляет значения по умолчанию")
    void createBranchWithOnlyName() {
        BranchRequest request = branchBuilder().build();

        BranchResponse created = createBranch(request);

        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertNull(created.getTitle()),
                () -> assertNull(created.getDescription()),
                () -> assertEquals("en", created.getLanguage()),
                () -> assertEquals("UTC", created.getTimeZone()),
                () -> assertEquals("LEARNER", created.getDefaultUserType()),
                () -> assertEquals("MANUAL", created.getSignupMode()),
                () -> assertNull(created.getAnnouncement()),
                () -> assertTrue(created.isActive()),
                () -> assertNotNull(created.getCreatedAt())
        );
    }

    @Test
    @DisplayName("Все поля заполнены -> 201, ответ совпадает с запросом")
    void createBranchWithAllFields() {
        BranchRequest request = branchBuilder()
                .title("Бишкек").description("Описание").language("ru").timeZone("Asia/Bishkek")
                .defaultUserType("TRAINER").signupMode("DIRECT").announcement("Объявление").active(false).build();

        BranchResponse created = createBranch(request);

        assertAll(
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertEquals("Бишкек", created.getTitle()),
                () -> assertEquals("Описание", created.getDescription()),
                () -> assertEquals("ru", created.getLanguage()),
                () -> assertEquals("Asia/Bishkek", created.getTimeZone()),
                () -> assertEquals("TRAINER", created.getDefaultUserType()),
                () -> assertEquals("DIRECT", created.getSignupMode()),
                () -> assertEquals("Объявление", created.getAnnouncement()),
                () -> assertFalse(created.isActive())
        );
    }

    @ParameterizedTest(name = "defaultUserType = {0}")
    @ValueSource(strings = {"SUPER_ADMIN", "ADMIN", "TRAINER", "LEARNER"})
    @DisplayName("Каждый допустимый defaultUserType -> 201")
    void createBranchWithEachUserType(String userType) {
        BranchResponse created = createBranch(branchBuilder().defaultUserType(userType).build());

        assertEquals(userType, created.getDefaultUserType());
    }

    @ParameterizedTest(name = "signupMode = {0}")
    @ValueSource(strings = {"MANUAL", "DIRECT"})
    @DisplayName("Каждый допустимый signupMode -> 201")
    void createBranchWithEachSignupMode(String mode) {
        BranchResponse created = createBranch(branchBuilder().signupMode(mode).build());

        assertEquals(mode, created.getSignupMode());
    }

    @Test
    @DisplayName("Граничные значения: name 60, title 120, description 255, announcement 2000 -> 201")
    void createBranchWithBoundaryValues() {
        String name = "n".repeat(50) + UUID.randomUUID().toString().substring(0, 10);

        BranchResponse created = createBranch(BranchRequest.builder().name(name)
                .title("t".repeat(120)).description("d".repeat(255)).announcement("a".repeat(2000)).build());

        assertEquals(name, created.getName());
    }

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", BranchRequest.builder().build()),
                Arguments.of("name пустой", BranchRequest.builder().name("").build()),
                Arguments.of("name пробелы", BranchRequest.builder().name("   ").build()),
                Arguments.of("name с заглавными", BranchRequest.builder().name("UPPER").build()),
                Arguments.of("name с пробелом", BranchRequest.builder().name("with space").build()),
                Arguments.of("name с подчёркиванием", BranchRequest.builder().name("under_score").build()),
                Arguments.of("name кириллицей", BranchRequest.builder().name("филиал").build()),
                Arguments.of("name 61 символ", BranchRequest.builder().name("n".repeat(61)).build()),
                Arguments.of("title 121 символ", branchBuilder().title("t".repeat(121)).build()),
                Arguments.of("description 256 символов", branchBuilder().description("d".repeat(256)).build()),
                Arguments.of("announcement 2001 символ", branchBuilder().announcement("a".repeat(2001)).build()),
                // ПАДАЕТ (баг бэка): 201 вместо 400 — language не валидируется
                Arguments.of("language невалидный", branchBuilder().language("xx").build()),
                // ПАДАЕТ (баг бэка): 201 вместо 400 — timeZone не валидируется
                Arguments.of("timeZone невалидный", branchBuilder().timeZone("Mars/Olympus").build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createBranchWithInvalidData(String caseName, BranchRequest request) {
        Response response = BRANCH_CONTROLLER.createBranchResponse(request);
        if (response.statusCode() == 201) {
            CREATED_BRANCHES.add(response.as(BranchResponse.class).getId());
        }

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Дубликат name -> 409")
    void createBranchWithDuplicateName() {
        BranchResponse first = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.createBranchResponse(branchBuilder().name(first.getName()).build());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {
            "",
            "not a json",
            "{\"name\": \"abc\",",
            "[]",
            "{\"name\":\"abc\",\"defaultUserType\":\"INVALID_ROLE\"}",
            "{\"name\":\"abc\",\"signupMode\":\"INVALID_MODE\"}",
            "{\"name\":\"abc\",\"active\":\"yes\"}"
    })
    @DisplayName("Некорректное тело -> 400")
    void createBranchWithMalformedBody(String body) {
        Response response = BRANCH_CONTROLLER.createBranchRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/branches и /api/branches/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/branches/{id} -> 200, данные совпадают")
    void getBranchById() {
        BranchResponse created = createBranch(branchBuilder().title("Title").build());

        Response response = BRANCH_CONTROLLER.getBranchResponse(created.getId());
        assertEquals(200, response.statusCode(), response.asString());

        BranchResponse actual = response.as(BranchResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), actual.getId()),
                () -> assertEquals(created.getName(), actual.getName()),
                () -> assertEquals("Title", actual.getTitle())
        );
    }

    @Test
    @DisplayName("Несуществующий филиал -> 404")
    void getUnknownBranch() {
        Response response = BRANCH_CONTROLLER.getBranchResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный id -> 400")
    void getBranchWithInvalidId(String id) {
        Response response = BRANCH_CONTROLLER.getBranchResponse(id);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/branches -> 200, созданный филиал в списке с members = 0")
    void getAllBranches() {
        BranchResponse created = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.getBranchesResponse();
        assertEquals(200, response.statusCode(), response.asString());

        BranchListItem item = Arrays.stream(response.as(BranchListItem[].class))
                .filter(i -> created.getId().equals(i.getBranch().getId()))
                .findFirst().orElse(null);
        assertNotNull(item, "Созданный филиал не найден в списке");
        assertEquals(0, item.getMembers());
    }

    // ---------- PUT /api/branches/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление филиала -> 200, GET показывает новые значения")
    void updateBranch() {
        BranchResponse created = createBranch(branchBuilder().build());
        BranchRequest update = branchBuilder().title("Новый title").announcement("Новое объявление").active(false).build();

        Response response = BRANCH_CONTROLLER.updateBranchResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        BranchResponse fromGet = BRANCH_CONTROLLER.getBranchResponse(created.getId()).as(BranchResponse.class);
        assertAll(
                () -> assertEquals(update.getName(), response.as(BranchResponse.class).getName()),
                () -> assertEquals(update.getName(), fromGet.getName()),
                () -> assertEquals("Новый title", fromGet.getTitle()),
                () -> assertEquals("Новое объявление", fromGet.getAnnouncement()),
                () -> assertFalse(fromGet.isActive())
        );
    }

    @Test
    @DisplayName("Своё же имя при обновлении не считается дубликатом")
    void updateBranchWithOwnName() {
        BranchResponse created = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.updateBranchResponse(created.getId(),
                BranchRequest.builder().name(created.getName()).title("changed").build());

        assertEquals(200, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Имя другого филиала при обновлении -> 409")
    void updateBranchWithDuplicateName() {
        BranchResponse first = createBranch(branchBuilder().build());
        BranchResponse second = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.updateBranchResponse(second.getId(),
                BranchRequest.builder().name(first.getName()).build());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateInvalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateBranchWithInvalidData(String caseName, BranchRequest request) {
        BranchResponse created = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.updateBranchResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    /** Те же случаи, что при создании, без языка и часового пояса (их бэк не проверяет) */
    static Stream<Arguments> updateInvalidData() {
        return invalidData().filter(a -> !a.get()[0].toString().startsWith("language")
                && !a.get()[0].toString().startsWith("timeZone"));
    }

    @Test
    @DisplayName("Обновление несуществующего филиала -> 404")
    void updateUnknownBranch() {
        Response response = BRANCH_CONTROLLER.updateBranchResponse(UNKNOWN_ID, branchBuilder().build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с невалидным id -> 400")
    void updateBranchWithInvalidId() {
        Response response = BRANCH_CONTROLLER.updateBranchRaw("abc", "{\"name\":\"x\"}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с некорректным телом -> 400")
    void updateBranchWithMalformedBody() {
        BranchResponse created = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.updateBranchRaw(created.getId(), "not a json");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE /api/branches/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление -> 204, дальше GET -> 404")
    void deleteBranch() {
        BranchResponse created = BRANCH_CONTROLLER.createBranch(branchBuilder().build());

        Response delete = BRANCH_CONTROLLER.deleteBranchResponse(created.getId());
        Response get = BRANCH_CONTROLLER.getBranchResponse(created.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, get.statusCode(), get.asString())
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404")
    void deleteBranchTwice() {
        BranchResponse created = BRANCH_CONTROLLER.createBranch(branchBuilder().build());
        BRANCH_CONTROLLER.deleteBranchResponse(created.getId());

        Response response = BRANCH_CONTROLLER.deleteBranchResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление несуществующего филиала -> 404")
    void deleteUnknownBranch() {
        Response response = BRANCH_CONTROLLER.deleteBranchResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление с невалидным id -> 400")
    void deleteBranchWithInvalidId() {
        Response response = BRANCH_CONTROLLER.deleteBranchResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление филиала с участниками -> 204, у пользователя филиал пропадает")
    void deleteBranchWithMembers() {
        BranchResponse branch = BRANCH_CONTROLLER.createBranch(branchBuilder().build());
        UserResponse user = createUser();
        BRANCH_CONTROLLER.addUserResponse(branch.getId(), user.getId());

        Response delete = BRANCH_CONTROLLER.deleteBranchResponse(branch.getId());
        Response userBranches = USER_CONTROLLER.userBranchesResponse(user.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(0, userBranches.jsonPath().getList("$").size())
        );
    }

    // ---------- участники: POST/DELETE /api/branches/{id}/users/{userId}, GET /api/branches/{id}/users ----------

    @Tag("smoke")
    @Test
    @DisplayName("Добавление пользователя -> 204, он в списке участников и members = 1")
    void addUserToBranch() {
        BranchResponse branch = createBranch(branchBuilder().build());
        UserResponse user = createUser();

        Response add = BRANCH_CONTROLLER.addUserResponse(branch.getId(), user.getId());
        assertEquals(204, add.statusCode(), add.asString());

        UserResponse[] members = BRANCH_CONTROLLER.branchUsersResponse(branch.getId()).as(UserResponse[].class);
        BranchResponse[] branchesOfUser = USER_CONTROLLER.userBranchesResponse(user.getId()).as(BranchResponse[].class);
        BranchListItem item = Arrays.stream(BRANCH_CONTROLLER.getBranchesResponse().as(BranchListItem[].class))
                .filter(i -> branch.getId().equals(i.getBranch().getId())).findFirst().orElseThrow();

        assertAll(
                () -> assertEquals(1, members.length),
                () -> assertEquals(user.getId(), members[0].getId()),
                () -> assertEquals(1, branchesOfUser.length, "Филиал не появился у пользователя"),
                () -> assertEquals(branch.getId(), branchesOfUser[0].getId()),
                () -> assertEquals(1, item.getMembers())
        );
    }

    @Test
    @DisplayName("Повторное добавление того же пользователя -> 204, дубликата нет")
    void addSameUserTwice() {
        BranchResponse branch = createBranch(branchBuilder().build());
        UserResponse user = createUser();
        BRANCH_CONTROLLER.addUserResponse(branch.getId(), user.getId());

        Response second = BRANCH_CONTROLLER.addUserResponse(branch.getId(), user.getId());

        UserResponse[] members = BRANCH_CONTROLLER.branchUsersResponse(branch.getId()).as(UserResponse[].class);
        assertAll(
                () -> assertEquals(204, second.statusCode(), second.asString()),
                () -> assertEquals(1, members.length)
        );
    }

    @Test
    @DisplayName("Добавление в несуществующий филиал -> 404")
    void addUserToUnknownBranch() {
        UserResponse user = createUser();

        Response response = BRANCH_CONTROLLER.addUserResponse(UNKNOWN_ID, user.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Добавление несуществующего пользователя -> 404")
    void addUnknownUserToBranch() {
        BranchResponse branch = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.addUserResponse(branch.getId(), UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Добавление с невалидными id -> 400")
    void addUserWithInvalidIds() {
        BranchResponse branch = createBranch(branchBuilder().build());
        UserResponse user = createUser();

        Response badBranch = BRANCH_CONTROLLER.addUserResponse("abc", user.getId());
        Response badUser = BRANCH_CONTROLLER.addUserResponse(branch.getId(), "abc");

        assertAll(
                () -> assertEquals(400, badBranch.statusCode(), badBranch.asString()),
                () -> assertEquals(400, badUser.statusCode(), badUser.asString())
        );
    }

    @Tag("smoke")
    @Test
    @DisplayName("Удаление пользователя из филиала -> 204, список участников пустой")
    void removeUserFromBranch() {
        BranchResponse branch = createBranch(branchBuilder().build());
        UserResponse user = createUser();
        BRANCH_CONTROLLER.addUserResponse(branch.getId(), user.getId());

        Response remove = BRANCH_CONTROLLER.removeUserResponse(branch.getId(), user.getId());

        UserResponse[] members = BRANCH_CONTROLLER.branchUsersResponse(branch.getId()).as(UserResponse[].class);
        assertAll(
                () -> assertEquals(204, remove.statusCode(), remove.asString()),
                () -> assertEquals(0, members.length)
        );
    }

    @Test
    @DisplayName("Удаление пользователя, которого нет в филиале -> 204")
    void removeUserWhoIsNotMember() {
        BranchResponse branch = createBranch(branchBuilder().build());
        UserResponse user = createUser();

        Response response = BRANCH_CONTROLLER.removeUserResponse(branch.getId(), user.getId());

        assertEquals(204, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление с невалидными id -> 400")
    void removeUserWithInvalidIds() {
        Response response = BRANCH_CONTROLLER.removeUserResponse("abc", "abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Участники несуществующего филиала -> 404")
    void getUsersOfUnknownBranch() {
        Response response = BRANCH_CONTROLLER.branchUsersResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Участники с невалидным id -> 400")
    void getUsersOfBranchWithInvalidId() {
        Response response = BRANCH_CONTROLLER.branchUsersResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Новый филиал без участников -> пустой список")
    void newBranchHasNoMembers() {
        BranchResponse branch = createBranch(branchBuilder().build());

        Response response = BRANCH_CONTROLLER.branchUsersResponse(branch.getId());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("$").size())
        );
    }
}
