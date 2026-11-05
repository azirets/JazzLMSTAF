import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.NotificationController;
import kg.jazzlms.models.request.NotificationRuleRequest;
import kg.jazzlms.models.response.NotificationRuleResponse;
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
import java.util.stream.Stream;

import static kg.jazzlms.utils.EntityManager.ruleBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Notification service: правила уведомлений (/api/notifications)")
public class NotificationRuleTest {

    private static final NotificationController NOTIFICATIONS = new NotificationController(Endpoints.NOTIFICATION_URL);
    private static final List<String> CREATED = new ArrayList<>();
    /** id в notification-service — строки (ObjectId), поэтому "несуществующий" это просто другая строка */
    private static final String UNKNOWN_ID = "000000000000000000000000";
    private static final List<String> EVENT_TYPES = List.of("USER_CREATED", "USER_SIGNED_UP", "USER_LOGGED_IN", "COURSE_CREATED",
            "COURSE_UPDATED", "USER_ENROLLED", "COURSE_COMPLETED", "ASSIGNMENT_SUBMITTED", "UNIT_ADDED", "UNIT_COMPLETED",
            "COURSE_DELETED", "COURSE_RESTORED", "USER_DELETED", "USER_IMPERSONATED", "CERTIFICATE_ISSUED", "TEST_COMPLETED");
    private static final List<String> RECIPIENTS = List.of("RELATED_USER", "ACCOUNT_OWNER", "COURSE_INSTRUCTORS");

    private NotificationRuleResponse createRule(NotificationRuleRequest request) {
        NotificationRuleResponse created = NOTIFICATIONS.createRule(request);
        CREATED.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(NOTIFICATIONS::deleteRuleResponse);
    }

    // ---------- POST /api/notifications: positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Только обязательные поля -> 201, active = true и delayMinutes = 0 по умолчанию")
    void createRuleWithRequiredFields() {
        NotificationRuleRequest request = ruleBuilder().build();

        NotificationRuleResponse created = createRule(request);

        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertEquals(request.getEventType(), created.getEventType()),
                () -> assertEquals(request.getRecipient(), created.getRecipient()),
                () -> assertEquals(request.getSubjectTemplate(), created.getSubjectTemplate()),
                () -> assertEquals(request.getBodyTemplate(), created.getBodyTemplate()),
                () -> assertTrue(created.isActive()),
                () -> assertEquals(0, created.getDelayMinutes())
        );
    }

    @Test
    @DisplayName("Все поля заполнены -> 201, ответ совпадает с запросом")
    void createRuleWithAllFields() {
        NotificationRuleRequest request = ruleBuilder().eventType("COURSE_COMPLETED").recipient("COURSE_INSTRUCTORS")
                .active(false).delayMinutes(15).build();

        NotificationRuleResponse created = createRule(request);

        assertAll(
                () -> assertEquals("COURSE_COMPLETED", created.getEventType()),
                () -> assertEquals("COURSE_INSTRUCTORS", created.getRecipient()),
                () -> assertFalse(created.isActive()),
                () -> assertEquals(15, created.getDelayMinutes())
        );
    }

    @ParameterizedTest(name = "eventType = {0}")
    @MethodSource("eventTypes")
    @DisplayName("Каждый допустимый eventType -> 201")
    void createRuleWithEachEventType(String eventType) {
        NotificationRuleResponse created = createRule(ruleBuilder().eventType(eventType).build());

        assertEquals(eventType, created.getEventType());
    }

    static Stream<String> eventTypes() {
        return EVENT_TYPES.stream();
    }

    @ParameterizedTest(name = "recipient = {0}")
    @ValueSource(strings = {"RELATED_USER", "ACCOUNT_OWNER", "COURSE_INSTRUCTORS"})
    @DisplayName("Каждый допустимый recipient -> 201")
    void createRuleWithEachRecipient(String recipient) {
        NotificationRuleResponse created = createRule(ruleBuilder().recipient(recipient).build());

        assertEquals(recipient, created.getRecipient());
    }

    @Test
    @DisplayName("Граничные значения: name 100, delayMinutes 0 и 43200 -> 201")
    void createRuleWithBoundaryValues() {
        Response longName = NOTIFICATIONS.createRuleResponse(ruleBuilder().name("n".repeat(100)).build());
        Response maxDelay = NOTIFICATIONS.createRuleResponse(ruleBuilder().delayMinutes(43200).build());
        for (Response r : List.of(longName, maxDelay)) {
            if (r.statusCode() == 201) {
                CREATED.add(r.as(NotificationRuleResponse.class).getId());
            }
        }

        assertAll(
                () -> assertEquals(201, longName.statusCode(), longName.asString()),
                () -> assertEquals(201, maxDelay.statusCode(), maxDelay.asString())
        );
    }

    @Test
    @DisplayName("Правила с одинаковым name допустимы -> 201, разные id")
    void createRulesWithSameName() {
        NotificationRuleResponse first = createRule(ruleBuilder().name("Same name").build());
        NotificationRuleResponse second = createRule(ruleBuilder().name("Same name").build());

        assertNotEquals(first.getId(), second.getId());
    }

    // ---------- POST: negative ----------

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", ruleBuilder().name(null).build()),
                Arguments.of("name пустой", ruleBuilder().name("").build()),
                Arguments.of("name пробелы", ruleBuilder().name("   ").build()),
                Arguments.of("name 101 символ", ruleBuilder().name("n".repeat(101)).build()),
                Arguments.of("eventType = null", ruleBuilder().eventType(null).build()),
                Arguments.of("recipient = null", ruleBuilder().recipient(null).build()),
                Arguments.of("subjectTemplate = null", ruleBuilder().subjectTemplate(null).build()),
                Arguments.of("subjectTemplate пустой", ruleBuilder().subjectTemplate("").build()),
                Arguments.of("bodyTemplate = null", ruleBuilder().bodyTemplate(null).build()),
                Arguments.of("bodyTemplate пустой", ruleBuilder().bodyTemplate("").build()),
                Arguments.of("delayMinutes = -1", ruleBuilder().delayMinutes(-1).build()),
                Arguments.of("delayMinutes = 43201", ruleBuilder().delayMinutes(43201).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createRuleWithInvalidData(String caseName, NotificationRuleRequest request) {
        Response response = NOTIFICATIONS.createRuleResponse(request);
        if (response.statusCode() == 201) {
            CREATED.add(response.as(NotificationRuleResponse.class).getId());
        }

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {
            "",
            "{}",
            "not a json",
            "[]",
            "{\"name\":\"x\",\"eventType\":\"BAD\",\"recipient\":\"RELATED_USER\",\"subjectTemplate\":\"s\",\"bodyTemplate\":\"b\"}",
            "{\"name\":\"x\",\"eventType\":\"USER_CREATED\",\"recipient\":\"BAD\",\"subjectTemplate\":\"s\",\"bodyTemplate\":\"b\"}",
            "{\"name\":\"x\",\"eventType\":\"USER_CREATED\",\"recipient\":\"RELATED_USER\",\"subjectTemplate\":\"s\",\"bodyTemplate\":\"b\",\"delayMinutes\":\"soon\"}",
            "{\"name\":\"x\",\"eventType\":\"USER_CREATED\",\"recipient\":\"RELATED_USER\",\"subjectTemplate\":\"s\",\"bodyTemplate\":\"b\",\"active\":\"yes\"}"
    })
    @DisplayName("Некорректное тело -> 400")
    void createRuleWithMalformedBody(String body) {
        Response response = NOTIFICATIONS.createRuleRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/notifications и /{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/notifications -> 200, созданное правило в списке")
    void getAllRules() {
        NotificationRuleResponse created = createRule(ruleBuilder().build());

        Response response = NOTIFICATIONS.getRulesResponse();
        assertEquals(200, response.statusCode(), response.asString());

        NotificationRuleResponse[] rules = response.as(NotificationRuleResponse[].class);
        assertTrue(Arrays.stream(rules).anyMatch(r -> created.getId().equals(r.getId())), "Правило не найдено в списке");
    }

    @Test
    @DisplayName("В списке есть встроенные правила, и у каждого заполнены обязательные поля")
    void builtInRulesAreValid() {
        NotificationRuleResponse[] rules = NOTIFICATIONS.getRulesResponse().as(NotificationRuleResponse[].class);

        assertAll(
                () -> assertTrue(rules.length > 0),
                () -> assertTrue(Arrays.stream(rules).allMatch(r -> r.getId() != null && r.getName() != null
                        && EVENT_TYPES.contains(r.getEventType()) && RECIPIENTS.contains(r.getRecipient())
                        && r.getSubjectTemplate() != null && r.getBodyTemplate() != null))
        );
    }

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/notifications/{id} -> 200, данные совпадают")
    void getRuleById() {
        NotificationRuleResponse created = createRule(ruleBuilder().delayMinutes(5).build());

        Response response = NOTIFICATIONS.getRuleResponse(created.getId());
        assertEquals(200, response.statusCode(), response.asString());

        NotificationRuleResponse actual = response.as(NotificationRuleResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), actual.getId()),
                () -> assertEquals(created.getName(), actual.getName()),
                () -> assertEquals(5, actual.getDelayMinutes())
        );
    }

    @ParameterizedTest(name = "id = {0} -> 404")
    @ValueSource(strings = {UNKNOWN_ID, "abc", "123"})
    @DisplayName("Несуществующий или неверный по формату id -> 404")
    void getUnknownRule(String id) {
        Response response = NOTIFICATIONS.getRuleResponse(id);

        assertEquals(404, response.statusCode(), response.asString());
    }

    // ---------- PUT /api/notifications/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление -> 200, GET показывает новые значения")
    void updateRule() {
        NotificationRuleResponse created = createRule(ruleBuilder().build());
        NotificationRuleRequest update = ruleBuilder().eventType("COURSE_CREATED").recipient("ACCOUNT_OWNER")
                .active(false).delayMinutes(5).build();

        Response response = NOTIFICATIONS.updateRuleResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        NotificationRuleResponse fromGet = NOTIFICATIONS.getRuleResponse(created.getId()).as(NotificationRuleResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), response.as(NotificationRuleResponse.class).getId()),
                () -> assertEquals(update.getName(), fromGet.getName()),
                () -> assertEquals("COURSE_CREATED", fromGet.getEventType()),
                () -> assertEquals("ACCOUNT_OWNER", fromGet.getRecipient()),
                () -> assertFalse(fromGet.isActive()),
                () -> assertEquals(5, fromGet.getDelayMinutes()),
                () -> assertEquals(update.getSubjectTemplate(), fromGet.getSubjectTemplate()),
                () -> assertEquals(update.getBodyTemplate(), fromGet.getBodyTemplate())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateRuleWithInvalidData(String caseName, NotificationRuleRequest request) {
        NotificationRuleResponse created = createRule(ruleBuilder().build());

        Response response = NOTIFICATIONS.updateRuleResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление несуществующего правила -> 404")
    void updateUnknownRule() {
        Response response = NOTIFICATIONS.updateRuleResponse(UNKNOWN_ID, ruleBuilder().build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с некорректным телом -> 400")
    void updateRuleWithMalformedBody() {
        NotificationRuleResponse created = createRule(ruleBuilder().build());

        Response response = NOTIFICATIONS.updateRuleRaw(created.getId(), "not a json");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE /api/notifications/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление -> 204, дальше GET -> 404 и правила нет в списке")
    void deleteRule() {
        NotificationRuleResponse created = NOTIFICATIONS.createRule(ruleBuilder().build());

        Response delete = NOTIFICATIONS.deleteRuleResponse(created.getId());

        NotificationRuleResponse[] rules = NOTIFICATIONS.getRulesResponse().as(NotificationRuleResponse[].class);
        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, NOTIFICATIONS.getRuleResponse(created.getId()).statusCode()),
                () -> assertTrue(Arrays.stream(rules).noneMatch(r -> created.getId().equals(r.getId())))
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404")
    void deleteRuleTwice() {
        NotificationRuleResponse created = NOTIFICATIONS.createRule(ruleBuilder().build());
        NOTIFICATIONS.deleteRuleResponse(created.getId());

        Response response = NOTIFICATIONS.deleteRuleResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "id = {0} -> 404")
    @ValueSource(strings = {UNKNOWN_ID, "abc"})
    @DisplayName("Удаление несуществующего правила -> 404")
    void deleteUnknownRule(String id) {
        Response response = NOTIFICATIONS.deleteRuleResponse(id);

        assertEquals(404, response.statusCode(), response.asString());
    }

    // ---------- GET /api/notifications/recipients и /events ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /recipients -> ровно три получателя")
    void getRecipients() {
        Response response = NOTIFICATIONS.recipientsResponse();

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(RECIPIENTS, response.jsonPath().getList("$"))
        );
    }

    @Tag("smoke")
    @Test
    @DisplayName("GET /events -> все 16 типов событий")
    void getEvents() {
        Response response = NOTIFICATIONS.eventsResponse();

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(EVENT_TYPES, response.jsonPath().getList("$"))
        );
    }
}
