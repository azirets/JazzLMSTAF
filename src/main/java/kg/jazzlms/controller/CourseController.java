package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.CourseRequest;
import kg.jazzlms.models.request.EnrollRequest;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.EnrollmentResponse;
import kg.jazzlms.models.response.MyEnrollmentResponse;
import kg.jazzlms.utils.JsonUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static kg.jazzlms.constants.Endpoints.*;

public class CourseController extends ApiRequest {

    private static final String X_USER_ID = "X-User-Id";

    public CourseController(String url) {
        super(url);
    }

    public CourseController(String url, String token) {
        super(url, token);
    }

    // ---------- create ----------

    public CourseResponse createCourse(CourseRequest request) {
        return createCourseResponse(request).then().statusCode(201).extract().as(CourseResponse.class);
    }

    public Response createCourseResponse(CourseRequest request) {
        return post(COURSES, JsonUtils.toJson(request));
    }

    /**
     * POST с заголовком X-User-Id. Сервис всё равно берёт id из токена,
     * поэтому подставить чужой id так нельзя (на этом есть тест).
     */
    public Response createCourseResponse(CourseRequest request, UUID userId) {
        this.response = given().spec(requestSpecification)
                .header(X_USER_ID, userId.toString())
                .body(JsonUtils.toJson(request))
                .post(COURSES);
        return this.response;
    }

    /** Для битого JSON, неверных типов и пустого тела */
    public Response createCourseRaw(String body) {
        return post(COURSES, body);
    }

    // ---------- read ----------

    public Response getCourseByIdResponse(Object id) {
        return get(String.format(COURSES_ID, id));
    }

    public CourseResponse getCourseById(UUID id) {
        return getCourseByIdResponse(id).then().statusCode(200).extract().as(CourseResponse.class);
    }

    public List<CourseResponse> getAllCourses() {
        return Arrays.asList(get(COURSES).as(CourseResponse[].class));
    }

    /** GET /api/courses/catalog: [{ "course": {...}, "enrolled": true|false }] */
    public Response catalogResponse() {
        return get(COURSES_CATALOG);
    }

    // ---------- delete ----------

    /** Мягкое удаление: создатель курса или админ */
    public Response deleteCourseResponse(Object id) {
        return delete(String.format(COURSES_ID, id));
    }

    /** Окончательное удаление из корзины (только после мягкого, только админ) */
    public Response deleteCoursePermanentlyResponse(Object id) {
        return delete(String.format(COURSES_PERMANENT, id));
    }

    // ---------- enrollments ----------

    /** Самозапись из каталога, роль всегда LEARNER */
    public Response selfEnrollResponse(Object courseId) {
        return post(String.format(COURSES_SELF_ENROLL, courseId), "");
    }

    /** Запись пользователя на курс (staff) */
    public Response enrollResponse(UUID courseId, UUID userId, String role) {
        return post(String.format(COURSES_ENROLLMENTS, courseId), JsonUtils.toJson(new EnrollRequest(userId, role)));
    }

    public List<EnrollmentResponse> getCourseEnrollments(UUID courseId) {
        return Arrays.asList(get(String.format(COURSES_ENROLLMENTS, courseId))
                .then().statusCode(200).extract().as(EnrollmentResponse[].class));
    }

    /** Курсы текущего пользователя (по токену) */
    public List<MyEnrollmentResponse> getMyEnrollments() {
        return Arrays.asList(get(ENROLLMENTS_MY).then().statusCode(200).extract().as(MyEnrollmentResponse[].class));
    }

    public Response updateProgressResponse(Object enrollmentId, int progress) {
        return patch(String.format(ENROLLMENTS_PROGRESS, enrollmentId), JsonUtils.toJson(Map.of("progress", progress)));
    }

    public Response unenrollResponse(Object enrollmentId) {
        return delete(String.format(ENROLLMENTS_ID, enrollmentId));
    }

    // ---------- update / clone / restore / lists ----------

    /** PUT /api/courses/{id} — полная замена: поля, которых нет в запросе, обнуляются */
    public Response updateCourseResponse(Object id, CourseRequest request) {
        return put(String.format(COURSES_ID, id), JsonUtils.toJson(request));
    }

    public Response updateCourseRaw(Object id, String body) {
        return put(String.format(COURSES_ID, id), body);
    }

    public Response cloneCourseResponse(Object id) {
        return post(String.format(COURSES_CLONE, id), "");
    }

    public Response restoreCourseResponse(Object id) {
        return post(String.format(COURSES_RESTORE, id), "");
    }

    public Response getCoursesResponse() {
        return get(COURSES);
    }

    public Response searchCoursesResponse(String search) {
        return get(COURSES, Map.of("search", search));
    }

    public Response deletedCoursesResponse() {
        return get(COURSES_DELETED);
    }

    public Response courseStatsResponse() {
        return get(COURSES_STATS);
    }

    public Response courseFilesResponse(Object id) {
        return get(String.format(COURSES_FILES, id));
    }

    public Response reportUsersResponse(Object id) {
        return get(String.format(COURSES_REPORT_USERS, id));
    }

    public Response reportOverviewResponse(Object id) {
        return get(String.format(COURSES_REPORT_OVERVIEW, id));
    }

    public Response reportMatrixResponse(Object id) {
        return get(String.format(COURSES_REPORT_MATRIX, id));
    }

    // ---------- enrollments: дополнительные ----------

    public Response courseEnrollmentsResponse(Object courseId) {
        return get(String.format(COURSES_ENROLLMENTS, courseId));
    }

    public Response enrollRawResponse(Object courseId, String body) {
        return post(String.format(COURSES_ENROLLMENTS, courseId), body);
    }

    public Response myEnrollmentsResponse() {
        return get(ENROLLMENTS_MY);
    }

    public Response myEnrollmentsResponse(String role) {
        return get(ENROLLMENTS_MY, Map.of("role", role));
    }

    public Response myEnrollmentStatsResponse() {
        return get(ENROLLMENTS_MY_STATS);
    }

    public Response userEnrollmentsResponse(Object userId) {
        return get(String.format(ENROLLMENTS_USER, userId));
    }

    public Response updateProgressRaw(Object enrollmentId, String body) {
        return patch(String.format(ENROLLMENTS_PROGRESS, enrollmentId), body);
    }

    // ---------- categories (нужны для categoryId) ----------

    /** id любой существующей категории; если категорий нет — создаёт новую */
    public UUID getOrCreateCategoryId() {
        List<String> ids = get(CATEGORIES).then().statusCode(200).extract().jsonPath().getList("id");
        if (ids != null && !ids.isEmpty()) {
            return UUID.fromString(ids.get(0));
        }
        String body = JsonUtils.toJson(Map.of("name", "Auto category " + UUID.randomUUID().toString().substring(0, 8)));
        String id = post(CATEGORIES, body).then().statusCode(201).extract().path("id");
        return UUID.fromString(id);
    }
}
