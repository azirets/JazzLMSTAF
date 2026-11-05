package kg.jazzlms.controller;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.specification.MultiPartSpecification;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.TestRequest;
import kg.jazzlms.models.request.UnitRequest;
import kg.jazzlms.models.response.AttemptResponse;
import kg.jazzlms.models.response.TestResponse;
import kg.jazzlms.models.response.UnitResponse;
import kg.jazzlms.utils.JsonUtils;

import java.io.File;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static kg.jazzlms.constants.Endpoints.*;

/** Юниты (уроки) курса, тесты и попытки прохождения: /api/courses/{id}/units, /api/units/** */
public class UnitController extends ApiRequest {

    public UnitController(String url) {
        super(url);
    }

    public UnitController(String url, String token) {
        super(url, token);
    }

    // ---------- создание юнита (multipart: часть "unit" + необязательный "file") ----------

    public UnitResponse createUnit(Object courseId, UnitRequest request) {
        return createUnitResponse(courseId, request).then().statusCode(201).extract().as(UnitResponse.class);
    }

    public Response createUnitResponse(Object courseId, UnitRequest request) {
        return createUnitRaw(courseId, JsonUtils.toJson(request), null);
    }

    public Response createUnitResponse(Object courseId, UnitRequest request, File file) {
        return createUnitRaw(courseId, JsonUtils.toJson(request), file);
    }

    /** Часть "unit" как сырая строка JSON (битый JSON, пустой объект) */
    public Response createUnitRaw(Object courseId, String unitJson, File file) {
        // charset UTF-8 обязателен, иначе кириллица в названии превращается в "????"
        MultiPartSpecification unitPart = new MultiPartSpecBuilder(unitJson)
                .controlName("unit")
                .mimeType("application/json")
                .charset("UTF-8")
                .build();
        var spec = given().spec(requestSpecification)
                .contentType(ContentType.MULTIPART)
                .multiPart(unitPart);
        if (file != null) {
            spec = spec.multiPart("file", file);
        }
        this.response = spec.post(String.format(COURSES_UNITS, courseId));
        return this.response;
    }

    /** Запрос без части "unit" — только файл */
    public Response createUnitWithoutUnitPart(Object courseId, File file) {
        this.response = given().spec(requestSpecification)
                .contentType(ContentType.MULTIPART)
                .multiPart("file", file)
                .post(String.format(COURSES_UNITS, courseId));
        return this.response;
    }

    // ---------- чтение, изменение, удаление ----------

    public Response getUnitsResponse(Object courseId) {
        return get(String.format(COURSES_UNITS, courseId));
    }

    public Response getUnitResponse(Object id) {
        return get(String.format(UNITS_ID, id));
    }

    public Response updateUnitResponse(Object id, UnitRequest request) {
        return put(String.format(UNITS_ID, id), JsonUtils.toJson(request));
    }

    public Response updateUnitRaw(Object id, String body) {
        return put(String.format(UNITS_ID, id), body);
    }

    public Response deleteUnitResponse(Object id) {
        return delete(String.format(UNITS_ID, id));
    }

    public Response userFilesResponse(Object userId) {
        return get(String.format(UNITS_FILES_USER, userId));
    }

    // ---------- прохождение ----------

    public Response startResponse(Object id) {
        return post(String.format(UNITS_START, id), "");
    }

    public Response completeResponse(Object id, Map<String, String> body) {
        return post(String.format(UNITS_COMPLETE, id), JsonUtils.toJson(body));
    }

    /** body: {"position": N}; сырая строка, чтобы можно было отправить мусор */
    public Response moveResponse(Object id, String body) {
        return post(String.format(UNITS_MOVE, id), body);
    }

    // ---------- тесты ----------

    public TestResponse createTest(Object courseId, TestRequest request) {
        return createTestResponse(courseId, request).then().statusCode(201).extract().as(TestResponse.class);
    }

    public Response createTestResponse(Object courseId, TestRequest request) {
        return post(String.format(COURSES_TESTS, courseId), JsonUtils.toJson(request));
    }

    public Response createTestRaw(Object courseId, String body) {
        return post(String.format(COURSES_TESTS, courseId), body);
    }

    public Response getTestResponse(Object unitId) {
        return get(String.format(UNITS_TEST, unitId));
    }

    public Response updateTestResponse(Object unitId, TestRequest request) {
        return put(String.format(UNITS_TEST, unitId), JsonUtils.toJson(request));
    }

    public Response updateTestRaw(Object unitId, String body) {
        return put(String.format(UNITS_TEST, unitId), body);
    }

    // ---------- попытки ----------

    public Response startAttemptResponse(Object unitId) {
        return post(String.format(UNITS_ATTEMPTS, unitId), "");
    }

    public AttemptResponse startAttempt(Object unitId) {
        return startAttemptResponse(unitId).then().statusCode(201).extract().as(AttemptResponse.class);
    }

    public Response attemptsResponse(Object unitId) {
        return get(String.format(UNITS_ATTEMPTS, unitId));
    }

    public Response attemptsResponse(Object unitId, Object userId) {
        return get(String.format(UNITS_ATTEMPTS, unitId), Map.of("userId", userId));
    }

    public Response attemptResponse(Object unitId, Object attemptId) {
        return get(String.format(UNITS_ATTEMPT, unitId, attemptId));
    }

    /** body: ответы на вопросы (map questionId -> ответ); {} = ни на что не ответил */
    public Response submitAttemptResponse(Object unitId, Object attemptId, Map<String, Object> answers) {
        return post(String.format(UNITS_ATTEMPT_SUBMIT, unitId, attemptId), JsonUtils.toJson(answers));
    }
}
