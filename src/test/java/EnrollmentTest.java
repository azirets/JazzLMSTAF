import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.CourseRequest;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.EnrollmentResponse;
import kg.jazzlms.models.response.MyEnrollmentResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Запись на курс, прогресс, отписка, "мои курсы", отчёты и каталог (под администратором) */
@Tag("regression")
@DisplayName("Course service: записи на курс, прогресс, отчёты, каталог")
public class EnrollmentTest {

    private static final CourseController COURSE_CONTROLLER = new CourseController(Endpoints.COURSE_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<UUID> COURSES = new ArrayList<>();
    private static final List<UUID> USERS = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private CourseResponse createCourse() {
        return createCourse(EntityManager.generateCourse());
    }

    private CourseResponse createCourse(CourseRequest request) {
        CourseResponse created = COURSE_CONTROLLER.createCourse(request);
        COURSES.add(created.getId());
        return created;
    }

    /** Активный пользователь: неактивного нельзя записать на курс */
    private UserResponse createUser(boolean active) {
        UserResponse created = USER_CONTROLLER.createUser(EntityManager.userBuilder().active(active).build());
        USERS.add(created.getId());
        return created;
    }

    private EnrollmentResponse enroll(CourseResponse course, UserResponse user) {
        Response response = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "LEARNER");
        assertEquals(201, response.statusCode(), response.asString());
        return response.as(EnrollmentResponse.class);
    }

    @AfterAll
    static void cleanUp() {
        COURSES.forEach(id -> {
            COURSE_CONTROLLER.deleteCourseResponse(id);
            COURSE_CONTROLLER.deleteCoursePermanentlyResponse(id);
        });
        USERS.forEach(USER_CONTROLLER::deleteUserResponse);
    }

    // ---------- POST /api/courses/{id}/enrollments ----------

    @Tag("smoke")
    @Test
    @DisplayName("Запись пользователя -> 201, роль LEARNER, progress 0, данные пользователя из user-service")
    void enrollUser() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);

        EnrollmentResponse enrollment = enroll(course, user);

        assertAll(
                () -> assertNotNull(enrollment.getId()),
                () -> assertEquals(course.getId(), enrollment.getCourseId()),
                () -> assertEquals(user.getId(), enrollment.getUserId()),
                () -> assertEquals("LEARNER", enrollment.getRole()),
                () -> assertEquals(0, enrollment.getProgress()),
                () -> assertEquals(user.getEmail(), enrollment.getUserEmail()),
                () -> assertEquals(user.getFirstName() + " " + user.getLastName(), enrollment.getUserFullName()),
                () -> assertNull(enrollment.getCompletedAt())
        );
    }

    @Test
    @DisplayName("Роль INSTRUCTOR -> 201, роль сохраняется")
    void enrollInstructor() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);

        Response response = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "INSTRUCTOR");

        assertAll(
                () -> assertEquals(201, response.statusCode(), response.asString()),
                () -> assertEquals("INSTRUCTOR", response.as(EnrollmentResponse.class).getRole())
        );
    }

    @Test
    @DisplayName("Без роли -> 201, по умолчанию LEARNER")
    void enrollWithoutRoleDefaultsToLearner() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);

        Response response = COURSE_CONTROLLER.enrollRawResponse(course.getId(), "{\"userId\":\"" + user.getId() + "\"}");

        assertAll(
                () -> assertEquals(201, response.statusCode(), response.asString()),
                () -> assertEquals("LEARNER", response.as(EnrollmentResponse.class).getRole())
        );
    }

    @Tag("smoke")
    @Test
    @DisplayName("GET /enrollments: создатель курса (INSTRUCTOR) и записанный ученик в списке")
    void courseEnrollmentsList() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);
        enroll(course, user);

        List<EnrollmentResponse> enrollments = COURSE_CONTROLLER.getCourseEnrollments(course.getId());

        assertAll(
                () -> assertEquals(2, enrollments.size()),
                () -> assertTrue(enrollments.stream().anyMatch(e -> user.getId().equals(e.getUserId()) && "LEARNER".equals(e.getRole()))),
                () -> assertTrue(enrollments.stream().anyMatch(e -> course.getCreatedBy().equals(e.getUserId()) && "INSTRUCTOR".equals(e.getRole())))
        );
    }

    @Test
    @DisplayName("learnersCount курса растёт после записи ученика")
    void learnersCountGrows() {
        CourseResponse course = createCourse();
        enroll(course, createUser(true));
        enroll(course, createUser(true));

        CourseResponse fromGet = COURSE_CONTROLLER.getCourseById(course.getId());

        assertEquals(2, fromGet.getLearnersCount());
    }

    @Test
    @DisplayName("Повторная запись того же пользователя -> 409")
    void enrollTwice() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);
        enroll(course, user);

        Response response = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "LEARNER");

        assertEquals(409, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий пользователь -> 404")
    void enrollUnknownUser() {
        CourseResponse course = createCourse();

        Response response = COURSE_CONTROLLER.enrollResponse(course.getId(), UNKNOWN_ID, "LEARNER");

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Неактивный пользователь -> 404")
    void enrollInactiveUser() {
        CourseResponse course = createCourse();
        UserResponse inactive = createUser(false);

        Response response = COURSE_CONTROLLER.enrollResponse(course.getId(), inactive.getId(), "LEARNER");

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Запись на несуществующий курс -> 404")
    void enrollToUnknownCourse() {
        UserResponse user = createUser(true);

        Response response = COURSE_CONTROLLER.enrollResponse(UNKNOWN_ID, user.getId(), "LEARNER");

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "{}", "not a json", "[]", "{\"userId\":\"abc\"}", "{\"userId\":null}"})
    @DisplayName("Некорректное тело записи -> 400")
    void enrollWithMalformedBody(String body) {
        CourseResponse course = createCourse();

        Response response = COURSE_CONTROLLER.enrollRawResponse(course.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Неизвестная роль -> 400")
    void enrollWithInvalidRole() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);

        Response response = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "BOSS");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Запись и список записей с невалидным id курса -> 400")
    void enrollmentsWithInvalidCourseId() {
        Response enroll = COURSE_CONTROLLER.enrollRawResponse("abc", "{\"userId\":\"" + UNKNOWN_ID + "\"}");
        Response list = COURSE_CONTROLLER.courseEnrollmentsResponse("abc");

        assertAll(
                () -> assertEquals(400, enroll.statusCode(), enroll.asString()),
                () -> assertEquals(400, list.statusCode(), list.asString())
        );
    }

    @Test
    @DisplayName("Список записей несуществующего курса -> 404")
    void enrollmentsOfUnknownCourse() {
        Response response = COURSE_CONTROLLER.courseEnrollmentsResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    // ---------- POST /api/courses/{id}/enroll (самозапись) ----------

    @Test
    @DisplayName("Самозапись создателя курса, который уже INSTRUCTOR -> 409")
    void selfEnrollAlreadyEnrolled() {
        CourseResponse course = createCourse();

        Response response = COURSE_CONTROLLER.selfEnrollResponse(course.getId());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Самозапись на несуществующий курс -> 404")
    void selfEnrollUnknownCourse() {
        Response response = COURSE_CONTROLLER.selfEnrollResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Самозапись с невалидным id -> 400")
    void selfEnrollWithInvalidId() {
        Response response = COURSE_CONTROLLER.selfEnrollResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- PATCH /api/enrollments/{id}/progress ----------

    @Tag("smoke")
    @Test
    @DisplayName("Прогресс 50 -> курс не завершён, 100 -> completedAt проставлен")
    void updateProgress() {
        CourseResponse course = createCourse();
        EnrollmentResponse enrollment = enroll(course, createUser(true));

        Response half = COURSE_CONTROLLER.updateProgressResponse(enrollment.getId(), 50);
        Response full = COURSE_CONTROLLER.updateProgressResponse(enrollment.getId(), 100);

        assertAll(
                () -> assertEquals(200, half.statusCode(), half.asString()),
                () -> assertEquals(50, half.as(EnrollmentResponse.class).getProgress()),
                () -> assertNull(half.as(EnrollmentResponse.class).getCompletedAt()),
                () -> assertEquals(200, full.statusCode(), full.asString()),
                () -> assertEquals(100, full.as(EnrollmentResponse.class).getProgress()),
                () -> assertNotNull(full.as(EnrollmentResponse.class).getCompletedAt())
        );
    }

    @Test
    @DisplayName("Прогресс 0 -> 200")
    void updateProgressToZero() {
        CourseResponse course = createCourse();
        EnrollmentResponse enrollment = enroll(course, createUser(true));

        Response response = COURSE_CONTROLLER.updateProgressResponse(enrollment.getId(), 0);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.as(EnrollmentResponse.class).getProgress())
        );
    }

    @ParameterizedTest(name = "progress = {0}")
    @ValueSource(strings = {"101", "-1", "1000"})
    @DisplayName("Прогресс вне диапазона 0..100 -> 400")
    void updateProgressOutOfRange(String progress) {
        CourseResponse course = createCourse();
        EnrollmentResponse enrollment = enroll(course, createUser(true));

        // ПАДАЕТ (баг бэка): 200 вместо 400 — значение молча обрезается до 0..100 (101 даже завершает курс)
        Response response = COURSE_CONTROLLER.updateProgressRaw(enrollment.getId(), "{\"progress\":" + progress + "}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Тело без progress -> 400")
    void updateProgressWithoutValue() {
        CourseResponse course = createCourse();
        EnrollmentResponse enrollment = enroll(course, createUser(true));

        // ПАДАЕТ (баг бэка): 200 вместо 400 — отсутствующий progress сбрасывает прогресс в 0
        Response response = COURSE_CONTROLLER.updateProgressRaw(enrollment.getId(), "{}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "not a json", "[]", "{\"progress\":\"half\"}"})
    @DisplayName("Некорректное тело прогресса -> 400")
    void updateProgressWithMalformedBody(String body) {
        CourseResponse course = createCourse();
        EnrollmentResponse enrollment = enroll(course, createUser(true));

        Response response = COURSE_CONTROLLER.updateProgressRaw(enrollment.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Прогресс несуществующей записи -> 404")
    void updateProgressOfUnknownEnrollment() {
        Response response = COURSE_CONTROLLER.updateProgressResponse(UNKNOWN_ID, 10);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Прогресс с невалидным id -> 400")
    void updateProgressWithInvalidId() {
        Response response = COURSE_CONTROLLER.updateProgressResponse("abc", 10);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE /api/enrollments/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Отписка -> 204, пользователя нет в списке записей")
    void unenroll() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);
        EnrollmentResponse enrollment = enroll(course, user);

        Response delete = COURSE_CONTROLLER.unenrollResponse(enrollment.getId());

        List<EnrollmentResponse> enrollments = COURSE_CONTROLLER.getCourseEnrollments(course.getId());
        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertTrue(enrollments.stream().noneMatch(e -> user.getId().equals(e.getUserId())))
        );
    }

    @Test
    @DisplayName("После отписки можно записаться снова")
    void enrollAgainAfterUnenroll() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);
        COURSE_CONTROLLER.unenrollResponse(enroll(course, user).getId());

        Response response = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "LEARNER");

        assertEquals(201, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Повторная отписка -> 404")
    void unenrollTwice() {
        CourseResponse course = createCourse();
        EnrollmentResponse enrollment = enroll(course, createUser(true));
        COURSE_CONTROLLER.unenrollResponse(enrollment.getId());

        Response response = COURSE_CONTROLLER.unenrollResponse(enrollment.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Отписка по невалидному id -> 400")
    void unenrollWithInvalidId() {
        Response response = COURSE_CONTROLLER.unenrollResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/enrollments/my, /my/stats, /user/{userId} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /enrollments/my: созданный админом курс с ролью INSTRUCTOR")
    void myEnrollments() {
        CourseResponse course = createCourse();

        Response response = COURSE_CONTROLLER.myEnrollmentsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        MyEnrollmentResponse mine = Arrays.stream(response.as(MyEnrollmentResponse[].class))
                .filter(e -> course.getId().equals(e.getCourse().getId())).findFirst().orElse(null);
        assertNotNull(mine, "Курс не найден в 'Моих курсах'");
        assertEquals("INSTRUCTOR", mine.getRole());
    }

    @Test
    @DisplayName("Фильтр role=INSTRUCTOR оставляет курс, role=LEARNER убирает")
    void myEnrollmentsFilteredByRole() {
        CourseResponse course = createCourse();

        MyEnrollmentResponse[] instructor = COURSE_CONTROLLER.myEnrollmentsResponse("INSTRUCTOR").as(MyEnrollmentResponse[].class);
        MyEnrollmentResponse[] learner = COURSE_CONTROLLER.myEnrollmentsResponse("LEARNER").as(MyEnrollmentResponse[].class);

        assertAll(
                () -> assertTrue(Arrays.stream(instructor).anyMatch(e -> course.getId().equals(e.getCourse().getId()))),
                () -> assertTrue(Arrays.stream(learner).noneMatch(e -> course.getId().equals(e.getCourse().getId())))
        );
    }

    @Test
    @DisplayName("Неизвестная роль в фильтре -> 400")
    void myEnrollmentsWithInvalidRole() {
        Response response = COURSE_CONTROLLER.myEnrollmentsResponse("BOSS");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("GET /enrollments/my/stats -> 200, счётчики неотрицательные")
    void myEnrollmentStats() {
        Response response = COURSE_CONTROLLER.myEnrollmentStatsResponse();

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertTrue(response.jsonPath().getInt("trainingSeconds") >= 0),
                () -> assertTrue(response.jsonPath().getInt("coursesInProgress") >= 0)
        );
    }

    @Test
    @DisplayName("GET /enrollments/user/{userId}: курсы записанного пользователя")
    void userEnrollments() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);
        EnrollmentResponse enrollment = enroll(course, user);

        Response response = COURSE_CONTROLLER.userEnrollmentsResponse(user.getId());
        assertEquals(200, response.statusCode(), response.asString());

        MyEnrollmentResponse[] items = response.as(MyEnrollmentResponse[].class);
        assertAll(
                () -> assertEquals(1, items.length),
                () -> assertEquals(enrollment.getId(), items[0].getEnrollmentId()),
                () -> assertEquals(course.getId(), items[0].getCourse().getId())
        );
    }

    @Test
    @DisplayName("Записи пользователя без курсов -> 200 и пустой список")
    void userEnrollmentsOfUnknownUser() {
        Response response = COURSE_CONTROLLER.userEnrollmentsResponse(UNKNOWN_ID);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("$").size())
        );
    }

    @Test
    @DisplayName("Записи пользователя с невалидным id -> 400")
    void userEnrollmentsWithInvalidId() {
        Response response = COURSE_CONTROLLER.userEnrollmentsResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/courses/catalog ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /courses/catalog: видимый курс есть в каталоге, скрытый нет")
    void catalogContainsOnlyVisibleCourses() {
        CourseResponse visible = createCourse();
        CourseResponse hidden = createCourse(EntityManager.courseBuilder().hiddenFromCatalog(true).build());

        Response response = COURSE_CONTROLLER.catalogResponse();
        assertEquals(200, response.statusCode(), response.asString());

        List<String> ids = response.jsonPath().getList("course.id");
        assertAll(
                () -> assertTrue(ids.contains(visible.getId().toString()), "Видимого курса нет в каталоге"),
                () -> assertFalse(ids.contains(hidden.getId().toString()), "Скрытый курс попал в каталог")
        );
    }

    // ---------- отчёты: /api/courses/{id}/report/* ----------

    @Test
    @DisplayName("report/users: строки по преподавателю и ученику")
    void reportUsers() {
        CourseResponse course = createCourse();
        UserResponse user = createUser(true);
        enroll(course, user);

        Response response = COURSE_CONTROLLER.reportUsersResponse(course.getId());
        assertEquals(200, response.statusCode(), response.asString());

        List<String> userIds = response.jsonPath().getList("userId");
        assertAll(
                () -> assertEquals(2, userIds.size()),
                () -> assertTrue(userIds.contains(user.getId().toString())),
                () -> assertTrue(userIds.contains(course.getCreatedBy().toString()))
        );
    }

    @Test
    @DisplayName("report/overview: счётчики учеников и преподавателей")
    void reportOverview() {
        CourseResponse course = createCourse();
        enroll(course, createUser(true));
        EnrollmentResponse done = enroll(course, createUser(true));
        COURSE_CONTROLLER.updateProgressResponse(done.getId(), 100);

        Response response = COURSE_CONTROLLER.reportOverviewResponse(course.getId());
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals(2, response.jsonPath().getInt("assignedLearners")),
                () -> assertEquals(1, response.jsonPath().getInt("completedLearners")),
                () -> assertEquals(1, response.jsonPath().getInt("instructors")),
                () -> assertEquals(0, response.jsonPath().getInt("units"))
        );
    }

    @Test
    @DisplayName("report/matrix нового курса -> 200, пустые units и rows")
    void reportMatrix() {
        CourseResponse course = createCourse();

        Response response = COURSE_CONTROLLER.reportMatrixResponse(course.getId());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("units").size()),
                () -> assertNotNull(response.jsonPath().getList("rows"))
        );
    }

    @Test
    @DisplayName("Отчёты несуществующего курса -> 404")
    void reportsOfUnknownCourse() {
        Response users = COURSE_CONTROLLER.reportUsersResponse(UNKNOWN_ID);
        Response overview = COURSE_CONTROLLER.reportOverviewResponse(UNKNOWN_ID);
        Response matrix = COURSE_CONTROLLER.reportMatrixResponse(UNKNOWN_ID);

        assertAll(
                () -> assertEquals(404, users.statusCode(), users.asString()),
                () -> assertEquals(404, overview.statusCode(), overview.asString()),
                () -> assertEquals(404, matrix.statusCode(), matrix.asString())
        );
    }

    @Test
    @DisplayName("Отчёты с невалидным id -> 400")
    void reportsWithInvalidId() {
        Response users = COURSE_CONTROLLER.reportUsersResponse("abc");
        Response overview = COURSE_CONTROLLER.reportOverviewResponse("abc");
        Response matrix = COURSE_CONTROLLER.reportMatrixResponse("abc");

        assertAll(
                () -> assertEquals(400, users.statusCode(), users.asString()),
                () -> assertEquals(400, overview.statusCode(), overview.asString()),
                () -> assertEquals(400, matrix.statusCode(), matrix.asString())
        );
    }
}
