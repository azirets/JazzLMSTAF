package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.response.TimelinePageResponse;

import java.util.Map;

import static kg.jazzlms.constants.Endpoints.*;

/** /api/analytics/** (analytics service): только чтение */
public class AnalyticsController extends ApiRequest {

    public AnalyticsController(String url) {
        super(url);
    }

    public AnalyticsController(String url, String token) {
        super(url, token);
    }

    public Response summaryResponse() {
        return get(ANALYTICS_SUMMARY);
    }

    public Response overviewResponse() {
        return get(ANALYTICS_OVERVIEW);
    }

    /** period: today | yesterday | week | month; без параметра = today */
    public Response dashboardResponse() {
        return get(ANALYTICS_DASHBOARD);
    }

    public Response dashboardResponse(String period) {
        return get(ANALYTICS_DASHBOARD, Map.of("period", period));
    }

    public Response topUsersResponse() {
        return get(ANALYTICS_TOP_USERS);
    }

    public Response topUsersResponse(Object limit) {
        return get(ANALYTICS_TOP_USERS, Map.of("limit", limit));
    }

    /** Лента: любые из from, to, type, userId, courseId, page (с 0), size */
    public Response timelineResponse(Map<String, ?> queryParams) {
        return get(ANALYTICS_TIMELINE, queryParams);
    }

    public Response timelineResponse() {
        return get(ANALYTICS_TIMELINE);
    }

    public TimelinePageResponse timeline(Map<String, ?> queryParams) {
        return timelineResponse(queryParams).then().statusCode(200).extract().as(TimelinePageResponse.class);
    }

    public Response timelineExportResponse(Map<String, ?> queryParams) {
        return get(ANALYTICS_TIMELINE_EXPORT, queryParams);
    }

    public Response timelineExportResponse() {
        return get(ANALYTICS_TIMELINE_EXPORT);
    }

    public Response userActivityResponse(Object userId) {
        return get(String.format(ANALYTICS_USER_ACTIVITY, userId));
    }

    public Response userActivityResponse(Object userId, String period) {
        return get(String.format(ANALYTICS_USER_ACTIVITY, userId), Map.of("period", period));
    }

    public Response courseActivityResponse(Object courseId) {
        return get(String.format(ANALYTICS_COURSE_ACTIVITY, courseId));
    }

    public Response courseActivityResponse(Object courseId, String period) {
        return get(String.format(ANALYTICS_COURSE_ACTIVITY, courseId), Map.of("period", period));
    }
}
