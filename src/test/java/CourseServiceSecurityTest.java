import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AuthController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Все эндпоинты course-service (в Swagger нет публичных): неверный токен -> 401,
 * токен не-админа при прямом вызове сервиса (мимо gateway) -> 401.
 */
@Tag("regression")
@DisplayName("Course service: доступ с неверным токеном и с токеном не-админа")
public class CourseServiceSecurityTest {

    private static final UserController ADMIN_USERS = new UserController(Endpoints.BASE_URL);
    private static final String ID = "00000000-0000-0000-0000-000000000000";
    private static UUID learnerId;
    private static String learnerToken;

    @BeforeAll
    static void createLearner() {
        UserRequest request = EntityManager.loginableUserBuilder().build();
        UserResponse learner = ADMIN_USERS.createUser(request);
        learnerId = learner.getId();
        learnerToken = new AuthController(Endpoints.BASE_URL).login(request.getUsername(), request.getPassword()).getToken();
    }

    @AfterAll
    static void cleanUp() {
        ADMIN_USERS.deleteUserResponse(learnerId);
    }

    /** "METHOD путь" без ведущего слеша, как в Endpoints */
    private static Response call(ApiRequest api, String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        String path = parts[1].replace("{id}", ID).replace("{courseId}", ID).replace("{userId}", ID).replace("{attemptId}", ID);
        return switch (parts[0]) {
            case "GET" -> api.get(path);
            case "POST" -> api.post(path, "{}");
            case "PUT" -> api.put(path, "{}");
            case "PATCH" -> api.patch(path, "{}");
            default -> api.delete(path);
        };
    }

    private static final String[] ENDPOINTS = {
            // courses
            "GET api/courses", "POST api/courses", "GET api/courses/{id}", "PUT api/courses/{id}", "DELETE api/courses/{id}",
            "DELETE api/courses/{id}/permanent", "POST api/courses/{id}/restore", "POST api/courses/{id}/clone",
            "GET api/courses/stats", "GET api/courses/deleted", "GET api/courses/catalog", "GET api/courses/{id}/files",
            "GET api/courses/{id}/report/users", "GET api/courses/{id}/report/overview", "GET api/courses/{id}/report/matrix",
            // enrollments
            "GET api/courses/{id}/enrollments", "POST api/courses/{id}/enrollments", "POST api/courses/{id}/enroll",
            "PATCH api/enrollments/{id}/progress", "DELETE api/enrollments/{id}",
            "GET api/enrollments/my", "GET api/enrollments/my/stats", "GET api/enrollments/user/{userId}",
            // categories
            "GET api/categories", "POST api/categories", "PUT api/categories/{id}", "DELETE api/categories/{id}",
            // certificates
            "GET api/certificates/templates", "POST api/certificates/templates", "PUT api/certificates/templates/{id}",
            "DELETE api/certificates/templates/{id}", "POST api/certificates/templates/{id}/reset",
            "GET api/certificates/templates/{id}/preview", "GET api/certificates/templates/backgrounds",
            "GET api/certificates/{id}", "GET api/certificates/{id}/view", "GET api/certificates/user/{userId}", "GET api/certificates/my",
            // units
            "GET api/courses/{courseId}/units", "GET api/units/{id}", "PUT api/units/{id}", "DELETE api/units/{id}",
            "POST api/units/{id}/start", "POST api/units/{id}/move", "POST api/units/{id}/complete", "GET api/units/files/{userId}",
            // tests and attempts
            "POST api/courses/{courseId}/tests", "GET api/units/{id}/test", "PUT api/units/{id}/test",
            "GET api/units/{id}/test/attempts", "POST api/units/{id}/test/attempts",
            "GET api/units/{id}/test/attempts/{attemptId}", "POST api/units/{id}/test/attempts/{attemptId}/submit",
            // questions
            "GET api/courses/{courseId}/questions", "POST api/courses/{courseId}/questions",
            "POST api/courses/{courseId}/questions/import", "GET api/questions/{id}", "PUT api/questions/{id}", "DELETE api/questions/{id}"
    };

    static Stream<String> protectedEndpoints() {
        return Arrays.stream(ENDPOINTS);
    }

    @ParameterizedTest(name = "{0} с неверным токеном -> 401")
    @MethodSource("protectedEndpoints")
    @DisplayName("Неверный токен -> 401")
    void invalidTokenIsRejected(String endpoint) {
        Response response = call(new ApiRequest(Endpoints.COURSE_URL, "invalid-token"), endpoint);

        assertEquals(401, response.statusCode(), endpoint + ": " + response.asString());
    }

    @ParameterizedTest(name = "{0} с токеном LEARNER -> 401")
    @MethodSource("protectedEndpoints")
    @DisplayName("Токен не-админа -> 401")
    void learnerTokenIsRejected(String endpoint) {
        Response response = call(new ApiRequest(Endpoints.COURSE_URL, learnerToken), endpoint);

        assertEquals(401, response.statusCode(), endpoint + ": " + response.asString());
    }
}
