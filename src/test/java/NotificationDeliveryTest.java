import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.NotificationController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.NotificationMessageResponse;
import kg.jazzlms.models.response.NotificationRuleResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import kg.jazzlms.utils.Waiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static kg.jazzlms.utils.EntityManager.ruleBuilder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Письма появляются по событиям из других сервисов (через Kafka), поэтому после действия
 * ждём письмо в истории или в ожидающих (до 15 секунд).
 */
@Tag("regression")
@DisplayName("Notification service: письма (pending, history, удаление)")
public class NotificationDeliveryTest {

    private static final NotificationController NOTIFICATIONS = new NotificationController(Endpoints.NOTIFICATION_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<String> RULES = new ArrayList<>();
    private static final List<UUID> USERS = new ArrayList<>();
    private static final List<String> MESSAGES = new ArrayList<>();
    private static final List<String> EMAILS = new ArrayList<>();
    private static final int WAIT_SECONDS = 15;

    private NotificationRuleResponse createRule(kg.jazzlms.models.request.NotificationRuleRequest request) {
        NotificationRuleResponse created = NOTIFICATIONS.createRule(request);
        RULES.add(created.getId());
        return created;
    }

    /** Создаёт пользователя: это событие USER_CREATED, по которому срабатывают правила */
    private UserResponse createUser() {
        UserRequest request = EntityManager.userBuilder().firstName("Aibek").lastName("Tester").build();
        UserResponse created = USER_CONTROLLER.createUser(request);
        USERS.add(created.getId());
        EMAILS.add(created.getEmail());
        return created;
    }

    private Optional<NotificationMessageResponse> historyMessage(UserResponse user, NotificationRuleResponse rule) {
        return NOTIFICATIONS.historyOf(user.getEmail()).stream().filter(m -> rule.getId().equals(m.getRuleId())).findFirst();
    }

    private Optional<NotificationMessageResponse> pendingMessage(UserResponse user, NotificationRuleResponse rule) {
        return NOTIFICATIONS.pendingOf(user.getEmail()).stream().filter(m -> rule.getId().equals(m.getRuleId())).findFirst();
    }

    private NotificationMessageResponse awaitHistory(UserResponse user, NotificationRuleResponse rule) {
        assertTrue(Waiter.waitUntil(WAIT_SECONDS, () -> historyMessage(user, rule).isPresent()),
                "Письмо не пришло в историю за " + WAIT_SECONDS + " секунд");
        NotificationMessageResponse message = historyMessage(user, rule).orElseThrow();
        MESSAGES.add(message.getId());
        return message;
    }

    @AfterAll
    static void cleanUp() {
        MESSAGES.forEach(NOTIFICATIONS::deleteMessageResponse);
        // встроенные правила тоже присылают письма на адреса тестовых пользователей — убираем и их
        EMAILS.forEach(email -> {
            NOTIFICATIONS.historyOf(email).forEach(m -> NOTIFICATIONS.deleteMessageResponse(m.getId()));
            NOTIFICATIONS.pendingOf(email).forEach(m -> NOTIFICATIONS.deleteMessageResponse(m.getId()));
        });
        RULES.forEach(NOTIFICATIONS::deleteRuleResponse);
        USERS.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    // ---------- отправка по правилу ----------

    @Tag("smoke")
    @Test
    @DisplayName("Событие USER_CREATED -> письмо в истории: статус SENT, шаблоны подставлены")
    void messageIsSentByRule() {
        NotificationRuleResponse rule = createRule(ruleBuilder().subjectTemplate("Welcome {{firstName}}")
                .bodyTemplate("Hello {{firstName}} {{lastName}}").build());

        UserResponse user = createUser();
        NotificationMessageResponse message = awaitHistory(user, rule);

        assertAll(
                () -> assertEquals("SENT", message.getStatus()),
                () -> assertEquals(rule.getName(), message.getRuleName()),
                () -> assertEquals("USER_CREATED", message.getEventType()),
                () -> assertEquals(user.getEmail(), message.getRecipientEmail()),
                () -> assertEquals("Welcome Aibek", message.getSubject()),
                () -> assertEquals("Hello Aibek Tester", message.getBody()),
                () -> assertNotNull(message.getCreatedAt()),
                () -> assertNotNull(message.getSentAt())
        );
    }

    @Test
    @DisplayName("Письмо отправляется один раз на одно событие")
    void messageIsSentOnce() {
        NotificationRuleResponse rule = createRule(ruleBuilder().build());

        UserResponse user = createUser();
        awaitHistory(user, rule);

        long count = NOTIFICATIONS.historyOf(user.getEmail()).stream().filter(m -> rule.getId().equals(m.getRuleId())).count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Выключенное правило (active = false) писем не отправляет")
    void inactiveRuleSendsNothing() {
        NotificationRuleResponse active = createRule(ruleBuilder().build());
        NotificationRuleResponse inactive = createRule(ruleBuilder().active(false).build());

        UserResponse user = createUser();
        awaitHistory(user, active); // событие точно обработано, значит и неактивное правило уже проверено

        assertTrue(historyMessage(user, inactive).isEmpty(), "Выключенное правило отправило письмо");
        assertTrue(pendingMessage(user, inactive).isEmpty(), "Выключенное правило поставило письмо в очередь");
    }

    @Test
    @DisplayName("Правило на другой тип события на USER_CREATED не срабатывает")
    void ruleForOtherEventDoesNotFire() {
        NotificationRuleResponse matching = createRule(ruleBuilder().build());
        NotificationRuleResponse other = createRule(ruleBuilder().eventType("COURSE_DELETED").build());

        UserResponse user = createUser();
        awaitHistory(user, matching);

        assertTrue(historyMessage(user, other).isEmpty());
    }

    @Test
    @DisplayName("Удалённое правило больше не срабатывает")
    void deletedRuleDoesNotFire() {
        NotificationRuleResponse alive = createRule(ruleBuilder().build());
        NotificationRuleResponse deleted = createRule(ruleBuilder().build());
        NOTIFICATIONS.deleteRuleResponse(deleted.getId());

        UserResponse user = createUser();
        awaitHistory(user, alive);

        assertTrue(historyMessage(user, deleted).isEmpty());
    }

    // ---------- отложенные письма: GET /api/notifications/pending ----------

    @Tag("smoke")
    @Test
    @DisplayName("Правило с delayMinutes > 0 -> письмо в pending со статусом PENDING и временем отправки")
    void delayedMessageIsPending() {
        NotificationRuleResponse rule = createRule(ruleBuilder().delayMinutes(60).build());

        UserResponse user = createUser();
        assertTrue(Waiter.waitUntil(WAIT_SECONDS, () -> pendingMessage(user, rule).isPresent()),
                "Письмо не попало в очередь за " + WAIT_SECONDS + " секунд");

        NotificationMessageResponse message = pendingMessage(user, rule).orElseThrow();
        MESSAGES.add(message.getId());
        assertAll(
                () -> assertEquals("PENDING", message.getStatus()),
                () -> assertNotNull(message.getScheduledAt()),
                () -> assertTrue(message.getScheduledAt().compareTo(message.getCreatedAt()) > 0, "Отправка должна быть позже создания"),
                () -> assertNull(message.getSentAt()),
                () -> assertTrue(historyMessage(user, rule).isEmpty(), "Отложенное письмо не должно быть в истории")
        );
    }

    @Test
    @DisplayName("GET /pending -> 200, все письма в статусе PENDING")
    void pendingContainsOnlyPendingMessages() {
        Response response = NOTIFICATIONS.pendingResponse();

        NotificationMessageResponse[] messages = response.as(NotificationMessageResponse[].class);
        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertTrue(java.util.Arrays.stream(messages).allMatch(m -> "PENDING".equals(m.getStatus())))
        );
    }

    // ---------- GET /api/notifications/history ----------

    @Test
    @DisplayName("GET /history -> 200, письма не в статусе PENDING, у каждого есть адресат и тема")
    void historyContainsFinishedMessages() {
        Response response = NOTIFICATIONS.historyResponse();

        NotificationMessageResponse[] messages = response.as(NotificationMessageResponse[].class);
        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertTrue(java.util.Arrays.stream(messages).noneMatch(m -> "PENDING".equals(m.getStatus()))),
                () -> assertTrue(java.util.Arrays.stream(messages).allMatch(m -> m.getRecipientEmail() != null && m.getSubject() != null))
        );
    }

    // ---------- DELETE /api/notifications/messages/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление письма из истории -> 204, письма там больше нет")
    void deleteHistoryMessage() {
        NotificationRuleResponse rule = createRule(ruleBuilder().build());
        UserResponse user = createUser();
        NotificationMessageResponse message = awaitHistory(user, rule);

        Response delete = NOTIFICATIONS.deleteMessageResponse(message.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertTrue(historyMessage(user, rule).isEmpty())
        );
    }

    @Test
    @DisplayName("Удаление ожидающего письма -> 204, письма в pending больше нет")
    void deletePendingMessage() {
        NotificationRuleResponse rule = createRule(ruleBuilder().delayMinutes(60).build());
        UserResponse user = createUser();
        assertTrue(Waiter.waitUntil(WAIT_SECONDS, () -> pendingMessage(user, rule).isPresent()));
        NotificationMessageResponse message = pendingMessage(user, rule).orElseThrow();

        Response delete = NOTIFICATIONS.deleteMessageResponse(message.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertTrue(pendingMessage(user, rule).isEmpty())
        );
    }

    @ParameterizedTest(name = "id = {0}")
    @ValueSource(strings = {"000000000000000000000000", "abc"})
    @DisplayName("Удаление несуществующего письма -> 204 (операция идемпотентна)")
    void deleteUnknownMessage(String id) {
        Response response = NOTIFICATIONS.deleteMessageResponse(id);

        assertEquals(204, response.statusCode(), response.asString());
    }

    // ---------- DELETE /api/notifications/history ----------

    @Disabled("Стирает всю историю писем во всём окружении — запускать вручную и только на тестовом стенде")
    @Test
    @DisplayName("DELETE /history -> 204, история пуста")
    void clearHistory() {
        Response response = NOTIFICATIONS.clearHistoryResponse();

        assertAll(
                () -> assertEquals(204, response.statusCode(), response.asString()),
                () -> assertEquals(0, NOTIFICATIONS.historyResponse().jsonPath().getList("$").size())
        );
    }
}
