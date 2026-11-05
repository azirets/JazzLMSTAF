import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.SettingsController;
import kg.jazzlms.models.request.SettingsRequest;
import kg.jazzlms.models.response.PublicSettingsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Настройки одни на весь сайт: перед каждым тестом запоминаем их, после — возвращаем как были */
@Tag("regression")
@DisplayName("User service: настройки (/api/settings)")
public class SettingsTest {

    private final SettingsController settingsController = new SettingsController(Endpoints.BASE_URL);
    private final AuthController authController = new AuthController(Endpoints.BASE_URL);
    private SettingsRequest original;

    @BeforeEach
    void rememberSettings() {
        original = settingsController.getSettings();
    }

    @AfterEach
    void restoreSettings() {
        settingsController.updateSettingsResponse(original);
    }

    // ---------- GET ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/settings -> 200, обязательные поля заполнены")
    void getSettings() {
        Response response = settingsController.getSettingsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        SettingsRequest settings = response.as(SettingsRequest.class);
        assertAll(
                () -> assertNotNull(settings.getSiteName()),
                () -> assertNotNull(settings.getDefaultLanguage()),
                () -> assertNotNull(settings.getDefaultTimeZone()),
                () -> assertNotNull(settings.getSignupMode()),
                () -> assertNotNull(settings.getDefaultUserType()),
                () -> assertNotNull(settings.getVisibleUserFormat()),
                () -> assertTrue(settings.getLockMinutes() >= 1)
        );
    }

    // ---------- PUT: positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление -> 200, GET и публичные настройки показывают новое значение")
    void updateSettings() {
        SettingsRequest update = original.toBuilder()
                .siteName("JazzLMS QA")
                .siteDescription("Описание сайта")
                .visibleUserFormat("LAST_FIRST")
                .build();

        Response response = settingsController.updateSettingsResponse(update);
        assertEquals(200, response.statusCode(), response.asString());

        SettingsRequest fromGet = settingsController.getSettings();
        PublicSettingsResponse publicSettings = authController.publicSettingsResponse().as(PublicSettingsResponse.class);
        assertAll(
                () -> assertEquals("JazzLMS QA", response.as(SettingsRequest.class).getSiteName()),
                () -> assertEquals("JazzLMS QA", fromGet.getSiteName()),
                () -> assertEquals("Описание сайта", fromGet.getSiteDescription()),
                () -> assertEquals("LAST_FIRST", fromGet.getVisibleUserFormat()),
                () -> assertEquals("JazzLMS QA", publicSettings.getSiteName()),
                () -> assertEquals("LAST_FIRST", publicSettings.getVisibleUserFormat())
        );
    }

    @ParameterizedTest(name = "signupMode = {0}")
    @ValueSource(strings = {"MANUAL", "DIRECT"})
    @DisplayName("signupMode влияет на signupAllowed в публичных настройках")
    void signupModeIsReflectedInPublicSettings(String mode) {
        Response response = settingsController.updateSettingsResponse(original.toBuilder().signupMode(mode).build());
        assertEquals(200, response.statusCode(), response.asString());

        PublicSettingsResponse publicSettings = authController.publicSettingsResponse().as(PublicSettingsResponse.class);
        assertEquals("DIRECT".equals(mode), publicSettings.isSignupAllowed());
    }

    @ParameterizedTest(name = "visibleUserFormat = {0}")
    @ValueSource(strings = {"FIRST_LAST", "LAST_FIRST", "USERNAME"})
    @DisplayName("Каждый допустимый visibleUserFormat -> 200")
    void updateEachVisibleUserFormat(String format) {
        Response response = settingsController.updateSettingsResponse(original.toBuilder().visibleUserFormat(format).build());

        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(format, settingsController.getSettings().getVisibleUserFormat());
    }

    @ParameterizedTest(name = "defaultUserType = {0}")
    @ValueSource(strings = {"SUPER_ADMIN", "ADMIN", "TRAINER", "LEARNER"})
    @DisplayName("Каждый допустимый defaultUserType -> 200")
    void updateEachDefaultUserType(String userType) {
        Response response = settingsController.updateSettingsResponse(original.toBuilder().defaultUserType(userType).build());

        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(userType, settingsController.getSettings().getDefaultUserType());
    }

    @Test
    @DisplayName("Граничные значения: passwordMaxAgeDays 1 и 3650, lockAfterAttempts 1 и 100, lockMinutes 1 и 100000")
    void updateWithBoundaryValues() {
        Response min = settingsController.updateSettingsResponse(original.toBuilder()
                .passwordMaxAgeDays(1).lockAfterAttempts(1).lockMinutes(1).build());
        Response max = settingsController.updateSettingsResponse(original.toBuilder()
                .passwordMaxAgeDays(3650).lockAfterAttempts(100).lockMinutes(100000)
                .siteName("n".repeat(100)).siteDescription("d".repeat(500)).build());

        assertAll(
                () -> assertEquals(200, min.statusCode(), min.asString()),
                () -> assertEquals(200, max.statusCode(), max.asString())
        );
    }

    @Test
    @DisplayName("Флаг passwordChangeOnFirstLogin сохраняется")
    void updatePasswordChangeOnFirstLogin() {
        Response response = settingsController.updateSettingsResponse(original.toBuilder().passwordChangeOnFirstLogin(true).build());

        assertEquals(200, response.statusCode(), response.asString());
        assertTrue(settingsController.getSettings().getPasswordChangeOnFirstLogin());
    }

    // ---------- PUT: negative ----------

    /** Валидная база: в каждом случае ломаем только одно поле */
    private static SettingsRequest.SettingsRequestBuilder valid() {
        return SettingsRequest.builder().siteName("JazzLMS").defaultLanguage("en").defaultTimeZone("UTC").lockMinutes(30);
    }

    static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of("siteName пустой", valid().siteName("").build()),
                Arguments.of("siteName пробелы", valid().siteName("   ").build()),
                Arguments.of("siteName 101 символ", valid().siteName("n".repeat(101)).build()),
                Arguments.of("siteDescription 501 символ", valid().siteDescription("d".repeat(501)).build()),
                Arguments.of("defaultLanguage пустой", valid().defaultLanguage("").build()),
                Arguments.of("defaultTimeZone пустой", valid().defaultTimeZone("").build()),
                Arguments.of("passwordMaxAgeDays = 0", valid().passwordMaxAgeDays(0).build()),
                Arguments.of("passwordMaxAgeDays = 3651", valid().passwordMaxAgeDays(3651).build()),
                Arguments.of("lockAfterAttempts = 0", valid().lockAfterAttempts(0).build()),
                Arguments.of("lockAfterAttempts = 101", valid().lockAfterAttempts(101).build()),
                Arguments.of("lockMinutes = 0", valid().lockMinutes(0).build()),
                Arguments.of("lockMinutes = 100001", valid().lockMinutes(100001).build()),
                Arguments.of("termsOfService 20001 символ", valid().termsOfService("t".repeat(20001)).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    @DisplayName("Невалидные данные -> 400")
    void updateWithInvalidData(String caseName, SettingsRequest request) {
        Response response = settingsController.updateSettingsResponse(request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Пустой объект {} -> 400 (обязательные поля)")
    void updateWithEmptyObject() {
        Response response = settingsController.updateSettingsRaw("{}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {
            "",
            "not a json",
            "[]",
            "{\"siteName\":\"JazzLMS\",\"defaultLanguage\":\"en\",\"defaultTimeZone\":\"UTC\",\"lockMinutes\":30,\"signupMode\":\"INVALID\"}",
            "{\"siteName\":\"JazzLMS\",\"defaultLanguage\":\"en\",\"defaultTimeZone\":\"UTC\",\"lockMinutes\":30,\"defaultUserType\":\"INVALID\"}",
            "{\"siteName\":\"JazzLMS\",\"defaultLanguage\":\"en\",\"defaultTimeZone\":\"UTC\",\"lockMinutes\":30,\"visibleUserFormat\":\"INVALID\"}",
            "{\"siteName\":\"JazzLMS\",\"defaultLanguage\":\"en\",\"defaultTimeZone\":\"UTC\",\"lockMinutes\":\"many\"}"
    })
    @DisplayName("Некорректное тело -> 400")
    void updateWithMalformedBody(String body) {
        Response response = settingsController.updateSettingsRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий defaultGroupId")
    void updateWithUnknownDefaultGroup() {
        Response response = settingsController.updateSettingsResponse(original.toBuilder()
                .defaultGroupId("00000000-0000-0000-0000-000000000000").build());

        // ПАДАЕТ (баг бэка), если вернётся 200: группа не проверяется на существование
        assertTrue(List.of(400, 404).contains(response.statusCode()),
                "Ожидали 400/404, получили " + response.statusCode() + ": " + response.asString());
    }
}
