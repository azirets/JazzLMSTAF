import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.response.ImportResultResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: импорт и экспорт пользователей")
public class UserImportExportTest {

    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final String CSV_HEADER = "firstName,lastName,email,username\n";
    private static final List<String> IMPORTED_USERNAMES = new ArrayList<>();

    @TempDir
    Path tempDir;

    @AfterAll
    static void cleanUp() {
        for (String username : IMPORTED_USERNAMES) {
            UserResponse[] found = USER_CONTROLLER.getUsersResponse(Map.of("search", username)).as(UserResponse[].class);
            Arrays.stream(found).forEach(u -> USER_CONTROLLER.deleteUserResponse(u.getId()));
        }
    }

    private File csv(String content) throws IOException {
        return Files.writeString(tempDir.resolve("users-" + UUID.randomUUID() + ".csv"), content, StandardCharsets.UTF_8).toFile();
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- POST /api/users/import ----------

    @Tag("smoke")
    @Test
    @DisplayName("Импорт двух новых пользователей -> created = 2, они есть в системе")
    void importNewUsers() throws IOException {
        String suffix = uniqueSuffix();
        String first = "imp1_" + suffix;
        String second = "imp2_" + suffix;
        IMPORTED_USERNAMES.add(first);
        IMPORTED_USERNAMES.add(second);
        File file = csv(CSV_HEADER
                + "Ivan,Petrov," + first + "@example.com," + first + "\n"
                + "Anna,Sidorova," + second + "@example.com," + second + "\n");

        Response response = USER_CONTROLLER.importResponse(file);
        assertEquals(200, response.statusCode(), response.asString());

        ImportResultResponse result = response.as(ImportResultResponse.class);
        UserResponse[] found = USER_CONTROLLER.getUsersResponse(Map.of("search", first)).as(UserResponse[].class);
        assertAll(
                () -> assertEquals(2, result.getCreated(), response.asString()),
                () -> assertTrue(result.getSkipped().isEmpty(), response.asString()),
                () -> assertEquals(1, found.length, "Импортированный пользователь не найден")
        );
    }

    @Test
    @DisplayName("Повторный импорт того же username пропускается с причиной")
    void importDuplicateUsernameIsSkipped() throws IOException {
        String name = "impdup_" + uniqueSuffix();
        IMPORTED_USERNAMES.add(name);
        File file = csv(CSV_HEADER + "Ivan,Petrov," + name + "@example.com," + name + "\n");
        USER_CONTROLLER.importResponse(file);

        Response response = USER_CONTROLLER.importResponse(file);
        assertEquals(200, response.statusCode(), response.asString());

        ImportResultResponse result = response.as(ImportResultResponse.class);
        assertAll(
                () -> assertEquals(0, result.getCreated()),
                () -> assertEquals(1, result.getSkipped().size(), response.asString()),
                () -> assertEquals(2, result.getSkipped().get(0).getLine()),
                () -> assertEquals(name, result.getSkipped().get(0).getValue()),
                () -> assertNotNull(result.getSkipped().get(0).getReason())
        );
    }

    @Test
    @DisplayName("Строка с недостающими колонками пропускается")
    void importRowWithMissingColumnsIsSkipped() throws IOException {
        Response response = USER_CONTROLLER.importResponse(csv("garbage\n"));
        assertEquals(200, response.statusCode(), response.asString());

        ImportResultResponse result = response.as(ImportResultResponse.class);
        assertAll(
                () -> assertEquals(0, result.getCreated()),
                () -> assertEquals(1, result.getSkipped().size(), response.asString()),
                () -> assertEquals(1, result.getSkipped().get(0).getLine())
        );
    }

    @Test
    @DisplayName("Смешанный файл: валидная строка создаётся, битая пропускается")
    void importMixedFile() throws IOException {
        String name = "impmix_" + uniqueSuffix();
        IMPORTED_USERNAMES.add(name);
        File file = csv(CSV_HEADER
                + "Ivan,Petrov," + name + "@example.com," + name + "\n"
                + "broken line\n");

        Response response = USER_CONTROLLER.importResponse(file);
        assertEquals(200, response.statusCode(), response.asString());

        ImportResultResponse result = response.as(ImportResultResponse.class);
        assertAll(
                () -> assertEquals(1, result.getCreated(), response.asString()),
                () -> assertEquals(1, result.getSkipped().size(), response.asString())
        );
    }

    @Test
    @DisplayName("Пустой файл -> 200 и created = 0")
    void importEmptyFile() throws IOException {
        Response response = USER_CONTROLLER.importResponse(csv(""));

        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(0, response.as(ImportResultResponse.class).getCreated());
    }

    // ---------- GET /api/users/export ----------

    @Tag("smoke")
    @Test
    @DisplayName("Экспорт -> 200, CSV с заголовком и созданным пользователем")
    void exportContainsCreatedUser() {
        UserResponse created = USER_CONTROLLER.createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.exportResponse();
        USER_CONTROLLER.deleteUserResponse(created.getId());

        assertEquals(200, response.statusCode(), response.asString());
        String body = response.asString();
        assertAll(
                () -> assertTrue(body.contains("firstName,lastName,email,username,userType,active"), "Нет заголовка CSV"),
                () -> assertTrue(body.contains(created.getUsername()), "Пользователь не попал в экспорт")
        );
    }

    @Test
    @DisplayName("Экспорт с search возвращает только найденных")
    void exportWithSearch() {
        UserResponse created = USER_CONTROLLER.createUser(EntityManager.generateUser());
        UserResponse other = USER_CONTROLLER.createUser(EntityManager.generateUser());

        Response response = USER_CONTROLLER.exportResponse(Map.of("search", created.getUsername()));
        USER_CONTROLLER.deleteUserResponse(created.getId());
        USER_CONTROLLER.deleteUserResponse(other.getId());

        assertEquals(200, response.statusCode(), response.asString());
        String body = response.asString();
        assertAll(
                () -> assertTrue(body.contains(created.getUsername())),
                () -> assertFalse(body.contains(other.getUsername()), "В экспорте лишний пользователь")
        );
    }

    @Test
    @DisplayName("Экспорт с active=false не содержит активных админа")
    void exportWithActiveFilter() {
        Response response = USER_CONTROLLER.exportResponse(Map.of("active", false));

        assertEquals(200, response.statusCode(), response.asString());
        assertFalse(response.asString().contains("\"admin\""), "Админ активен и не должен попасть в выборку");
    }
}
