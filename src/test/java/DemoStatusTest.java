import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.DemoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("User service: GET /api/demo/status/{code}")
public class DemoStatusTest {

    private final DemoController demoController = new DemoController(Endpoints.BASE_URL);

    @Tag("smoke")
    @Test
    @DisplayName("200 -> тело с message и status")
    void status200() {
        Response response = demoController.statusResponse(200);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(200, response.jsonPath().getInt("status")),
                () -> assertNotNull(response.jsonPath().getString("message"))
        );
    }

    @Test
    @DisplayName("201 -> тело с message и status")
    void status201() {
        Response response = demoController.statusResponse(201);

        assertAll(
                () -> assertEquals(201, response.statusCode(), response.asString()),
                () -> assertEquals(201, response.jsonPath().getInt("status"))
        );
    }

    @Test
    @DisplayName("204 -> пустое тело")
    void status204() {
        Response response = demoController.statusResponse(204);

        assertAll(
                () -> assertEquals(204, response.statusCode()),
                () -> assertTrue(response.asString().isEmpty())
        );
    }

    @Test
    @DisplayName("300 -> список alternatives")
    void status300() {
        Response response = demoController.statusResponse(300);

        assertAll(
                () -> assertEquals(300, response.statusCode(), response.asString()),
                () -> assertFalse(response.jsonPath().getList("alternatives").isEmpty())
        );
    }

    @Test
    @DisplayName("301 -> редирект")
    void status301() {
        Response response = demoController.statusResponse(301);

        assertEquals(301, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("403 -> тело с message и status")
    void status403() {
        Response response = demoController.statusResponse(403);

        assertAll(
                () -> assertEquals(403, response.statusCode(), response.asString()),
                () -> assertEquals(403, response.jsonPath().getInt("status"))
        );
    }

    @Test
    @DisplayName("500 -> Internal Server Error")
    void status500() {
        Response response = demoController.statusResponse(500);

        assertEquals(500, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "code = {0} -> 400")
    @ValueSource(strings = {"418", "404", "0", "999", "-1"})
    @DisplayName("Неподдерживаемый код -> 400")
    void unsupportedCode(String code) {
        Response response = demoController.statusResponse(code);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "code = {0} -> 400")
    @ValueSource(strings = {"abc", "2.5", "OK"})
    @DisplayName("Код не число -> 400")
    void codeIsNotNumber(String code) {
        Response response = demoController.statusResponse(code);

        assertEquals(400, response.statusCode(), response.asString());
    }
}
