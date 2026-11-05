package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.NotificationRuleRequest;
import kg.jazzlms.models.response.NotificationMessageResponse;
import kg.jazzlms.models.response.NotificationRuleResponse;
import kg.jazzlms.utils.JsonUtils;

import java.util.Arrays;
import java.util.List;

import static kg.jazzlms.constants.Endpoints.*;

/** /api/notifications/** (notification service): правила, ожидающие и отправленные письма */
public class NotificationController extends ApiRequest {

    public NotificationController(String url) {
        super(url);
    }

    public NotificationController(String url, String token) {
        super(url, token);
    }

    // ---------- правила ----------

    public NotificationRuleResponse createRule(NotificationRuleRequest request) {
        return createRuleResponse(request).then().statusCode(201).extract().as(NotificationRuleResponse.class);
    }

    public Response createRuleResponse(NotificationRuleRequest request) {
        return post(NOTIFICATIONS, JsonUtils.toJson(request));
    }

    public Response createRuleRaw(String body) {
        return post(NOTIFICATIONS, body);
    }

    public Response getRulesResponse() {
        return get(NOTIFICATIONS);
    }

    public Response getRuleResponse(Object id) {
        return get(String.format(NOTIFICATIONS_ID, id));
    }

    public Response updateRuleResponse(Object id, NotificationRuleRequest request) {
        return put(String.format(NOTIFICATIONS_ID, id), JsonUtils.toJson(request));
    }

    public Response updateRuleRaw(Object id, String body) {
        return put(String.format(NOTIFICATIONS_ID, id), body);
    }

    public Response deleteRuleResponse(Object id) {
        return delete(String.format(NOTIFICATIONS_ID, id));
    }

    // ---------- справочники ----------

    public Response recipientsResponse() {
        return get(NOTIFICATIONS_RECIPIENTS);
    }

    public Response eventsResponse() {
        return get(NOTIFICATIONS_EVENTS);
    }

    // ---------- письма ----------

    public Response pendingResponse() {
        return get(NOTIFICATIONS_PENDING);
    }

    public Response historyResponse() {
        return get(NOTIFICATIONS_HISTORY);
    }

    public Response clearHistoryResponse() {
        return delete(NOTIFICATIONS_HISTORY);
    }

    public Response deleteMessageResponse(Object id) {
        return delete(String.format(NOTIFICATIONS_MESSAGES_ID, id));
    }

    /** Письма из истории, отправленные на этот email (удобно искать по уникальному адресу из теста) */
    public List<NotificationMessageResponse> historyOf(String email) {
        return filterByEmail(historyResponse(), email);
    }

    public List<NotificationMessageResponse> pendingOf(String email) {
        return filterByEmail(pendingResponse(), email);
    }

    private static List<NotificationMessageResponse> filterByEmail(Response response, String email) {
        return Arrays.stream(response.then().statusCode(200).extract().as(NotificationMessageResponse[].class))
                .filter(m -> email.equals(m.getRecipientEmail()))
                .toList();
    }
}
