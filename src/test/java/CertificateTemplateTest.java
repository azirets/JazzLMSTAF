import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CertificateController;
import kg.jazzlms.models.request.CertificateTemplateRequest;
import kg.jazzlms.models.response.CertificateTemplateResponse;
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

import static kg.jazzlms.utils.EntityManager.templateBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Course service: шаблоны сертификатов (/api/certificates/templates)")
public class CertificateTemplateTest {

    private static final CertificateController CERT_CONTROLLER = new CertificateController(Endpoints.COURSE_URL);
    private static final List<UUID> CREATED = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String DEFAULT_TITLE = "Certificate of Completion";

    private CertificateTemplateResponse createTemplate(CertificateTemplateRequest request) {
        CertificateTemplateResponse created = CERT_CONTROLLER.createTemplate(request);
        CREATED.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(CERT_CONTROLLER::deleteTemplateResponse);
    }

    // ---------- POST ----------

    @Tag("smoke")
    @Test
    @DisplayName("Создание шаблона -> 201, ответ совпадает с запросом, не шаблон по умолчанию")
    void createTemplate() {
        CertificateTemplateRequest request = templateBuilder().signature("Director").build();

        CertificateTemplateResponse created = createTemplate(request);

        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertEquals(request.getBackground(), created.getBackground()),
                () -> assertEquals(request.getTitle(), created.getTitle()),
                () -> assertEquals(request.getBody(), created.getBody()),
                () -> assertEquals("Director", created.getSignature()),
                () -> assertNotNull(created.getCreatedAt()),
                () -> assertFalse(created.isDefault())
        );
    }

    @Test
    @DisplayName("Без signature -> 201 (поле необязательное)")
    void createTemplateWithoutSignature() {
        CertificateTemplateResponse created = createTemplate(templateBuilder().build());

        assertNull(created.getSignature());
    }

    @Test
    @DisplayName("Каждый доступный background -> 201")
    void createTemplateWithEachBackground() {
        String[] backgrounds = CERT_CONTROLLER.getBackgroundsResponse().as(String[].class);

        for (String background : backgrounds) {
            CertificateTemplateResponse created = createTemplate(templateBuilder().background(background).build());
            assertEquals(background, created.getBackground());
        }
    }

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", templateBuilder().name(null).build()),
                Arguments.of("name пустой", templateBuilder().name("").build()),
                Arguments.of("name 101 символ", templateBuilder().name("n".repeat(101)).build()),
                Arguments.of("background = null", templateBuilder().background(null).build()),
                Arguments.of("background неизвестный", templateBuilder().background("nonexistent").build()),
                Arguments.of("title = null", templateBuilder().title(null).build()),
                Arguments.of("title пустой", templateBuilder().title("").build()),
                Arguments.of("title 201 символ", templateBuilder().title("t".repeat(201)).build()),
                Arguments.of("body = null", templateBuilder().body(null).build()),
                Arguments.of("body пустой", templateBuilder().body("").build()),
                Arguments.of("body 5001 символ", templateBuilder().body("b".repeat(5001)).build()),
                Arguments.of("signature 101 символ", templateBuilder().signature("s".repeat(101)).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createTemplateWithInvalidData(String caseName, CertificateTemplateRequest request) {
        Response response = CERT_CONTROLLER.createTemplateResponse(request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "{}", "not a json", "[]", "{\"name\": \"abc\","})
    @DisplayName("Некорректное тело -> 400")
    void createTemplateWithMalformedBody(String body) {
        Response response = CERT_CONTROLLER.createTemplateRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /templates -> 200, созданный шаблон в списке, есть шаблон по умолчанию")
    void getAllTemplates() {
        CertificateTemplateResponse created = createTemplate(templateBuilder().build());

        Response response = CERT_CONTROLLER.getTemplatesResponse();
        assertEquals(200, response.statusCode(), response.asString());

        CertificateTemplateResponse[] templates = response.as(CertificateTemplateResponse[].class);
        assertTrue(Arrays.stream(templates).anyMatch(t -> created.getId().equals(t.getId())),
                "Созданный шаблон не найден в списке");
    }

    @Test
    @DisplayName("GET /templates/backgrounds -> 200, список фонов включает classic")
    void getBackgrounds() {
        Response response = CERT_CONTROLLER.getBackgroundsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        List<String> backgrounds = Arrays.asList(response.as(String[].class));
        assertAll(
                () -> assertFalse(backgrounds.isEmpty()),
                () -> assertTrue(backgrounds.contains("classic"))
        );
    }

    @Test
    @DisplayName("GET /templates/{id}/preview -> 200, HTML с заголовком шаблона")
    void previewTemplate() {
        CertificateTemplateResponse created = createTemplate(templateBuilder().title("Preview Title").build());

        Response response = CERT_CONTROLLER.previewTemplateResponse(created.getId());

        assertAll(
                () -> assertEquals(200, response.statusCode()),
                () -> assertTrue(response.asString().contains("Preview Title"), "В превью нет заголовка шаблона")
        );
    }

    @Test
    @DisplayName("Превью несуществующего шаблона -> 404")
    void previewUnknownTemplate() {
        Response response = CERT_CONTROLLER.previewTemplateResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Превью с невалидным id -> 400")
    void previewTemplateWithInvalidId() {
        Response response = CERT_CONTROLLER.previewTemplateResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- PUT ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление -> 200, список показывает новые значения")
    void updateTemplate() {
        CertificateTemplateResponse created = createTemplate(templateBuilder().build());
        CertificateTemplateRequest update = templateBuilder().background("gold").title("New title").body("New body").signature("Boss").build();

        Response response = CERT_CONTROLLER.updateTemplateResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        CertificateTemplateResponse updated = response.as(CertificateTemplateResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), updated.getId()),
                () -> assertEquals(update.getName(), updated.getName()),
                () -> assertEquals("gold", updated.getBackground()),
                () -> assertEquals("New title", updated.getTitle()),
                () -> assertEquals("New body", updated.getBody()),
                () -> assertEquals("Boss", updated.getSignature())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateTemplateWithInvalidData(String caseName, CertificateTemplateRequest request) {
        CertificateTemplateResponse created = createTemplate(templateBuilder().build());

        Response response = CERT_CONTROLLER.updateTemplateResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление несуществующего шаблона -> 404")
    void updateUnknownTemplate() {
        Response response = CERT_CONTROLLER.updateTemplateResponse(UNKNOWN_ID, templateBuilder().build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с невалидным id -> 400")
    void updateTemplateWithInvalidId() {
        Response response = CERT_CONTROLLER.updateTemplateRaw("abc", "{}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- POST /templates/{id}/reset ----------

    @Test
    @DisplayName("Сброс шаблона -> 200, текст возвращается к стандартному, имя сохраняется")
    void resetTemplate() {
        CertificateTemplateResponse created = createTemplate(templateBuilder().title("Custom title").body("Custom body").build());

        Response response = CERT_CONTROLLER.resetTemplateResponse(created.getId());
        assertEquals(200, response.statusCode(), response.asString());

        CertificateTemplateResponse reset = response.as(CertificateTemplateResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), reset.getId()),
                () -> assertEquals(created.getName(), reset.getName()),
                () -> assertEquals(DEFAULT_TITLE, reset.getTitle()),
                () -> assertNotEquals("Custom body", reset.getBody())
        );
    }

    @Test
    @DisplayName("Сброс несуществующего шаблона -> 404")
    void resetUnknownTemplate() {
        Response response = CERT_CONTROLLER.resetTemplateResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Сброс с невалидным id -> 400")
    void resetTemplateWithInvalidId() {
        Response response = CERT_CONTROLLER.resetTemplateResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление -> 204, шаблона больше нет в списке")
    void deleteTemplate() {
        CertificateTemplateResponse created = CERT_CONTROLLER.createTemplate(templateBuilder().build());

        Response delete = CERT_CONTROLLER.deleteTemplateResponse(created.getId());

        CertificateTemplateResponse[] templates = CERT_CONTROLLER.getTemplatesResponse().as(CertificateTemplateResponse[].class);
        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertTrue(Arrays.stream(templates).noneMatch(t -> created.getId().equals(t.getId())))
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404")
    void deleteTemplateTwice() {
        CertificateTemplateResponse created = CERT_CONTROLLER.createTemplate(templateBuilder().build());
        CERT_CONTROLLER.deleteTemplateResponse(created.getId());

        Response response = CERT_CONTROLLER.deleteTemplateResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление несуществующего шаблона -> 404")
    void deleteUnknownTemplate() {
        Response response = CERT_CONTROLLER.deleteTemplateResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление с невалидным id -> 400")
    void deleteTemplateWithInvalidId() {
        Response response = CERT_CONTROLLER.deleteTemplateResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }
}
