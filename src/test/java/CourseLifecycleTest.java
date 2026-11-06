import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CategoryController;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.models.request.CourseRequest;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static kg.jazzlms.utils.EntityManager.courseBuilder;
import static org.junit.jupiter.api.Assertions.*;

/** Курс после создания: чтение, список, поиск, изменение, клонирование, удаление и восстановление */
@Tag("regression")
@DisplayName("Course service: жизненный цикл курса (GET, PUT, clone, delete, restore)")
public class CourseLifecycleTest {

    private static final CourseController COURSE_CONTROLLER = new CourseController(Endpoints.COURSE_URL);
    private static final CategoryController CATEGORY_CONTROLLER = new CategoryController(Endpoints.COURSE_URL);
    private static final List<UUID> CREATED = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private CourseResponse createCourse(CourseRequest request) {
        CourseResponse created = COURSE_CONTROLLER.createCourse(request);
        CREATED.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(id -> {
            COURSE_CONTROLLER.deleteCourseResponse(id);
            COURSE_CONTROLLER.deleteCoursePermanentlyResponse(id);
        });
    }

    // ---------- GET /api/courses и /api/courses/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/courses -> 200, созданный курс в списке")
    void getAllCourses() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.getCoursesResponse();
        assertEquals(200, response.statusCode(), response.asString());

        CourseResponse[] courses = response.as(CourseResponse[].class);
        assertTrue(Arrays.stream(courses).anyMatch(c -> created.getId().equals(c.getId())),
                "Созданный курс не найден в списке");
    }

    @Test
    @DisplayName("search по названию находит курс")
    void searchByName() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.searchCoursesResponse(created.getName());
        assertEquals(200, response.statusCode(), response.asString());

        CourseResponse[] courses = response.as(CourseResponse[].class);
        assertTrue(Arrays.stream(courses).anyMatch(c -> created.getId().equals(c.getId())));
    }

    @Test
    @DisplayName("search без совпадений -> 200 и пустой список")
    void searchWithoutMatches() {
        Response response = COURSE_CONTROLLER.searchCoursesResponse("no-such-course-" + UUID.randomUUID());

        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(0, response.as(CourseResponse[].class).length);
    }

    @Test
    @DisplayName("Несуществующий курс -> 404")
    void getUnknownCourse() {
        Response response = COURSE_CONTROLLER.getCourseByIdResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "id = {0} -> 400")
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Невалидный id -> 400")
    void getCourseWithInvalidId(String id) {
        Response response = COURSE_CONTROLLER.getCourseByIdResponse(id);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/courses/stats, /deleted ----------

    @Test
    @DisplayName("GET /api/courses/stats -> 200, все счётчики неотрицательные")
    void getCourseStats() {
        Response response = COURSE_CONTROLLER.courseStatsResponse();
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertTrue(response.jsonPath().getInt("assignedCourses") >= 0),
                () -> assertTrue(response.jsonPath().getInt("activeCourses") >= 0),
                () -> assertTrue(response.jsonPath().getInt("inProgress") >= 0),
                () -> assertTrue(response.jsonPath().getInt("courseCompletions") >= 0),
                () -> assertTrue(response.jsonPath().getInt("trainingSeconds") >= 0)
        );
    }

    // ---------- PUT /api/courses/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление -> 200, GET показывает новые значения")
    void updateCourse() {
        CourseResponse created = createCourse(EntityManager.generateCourse());
        UUID categoryId = COURSE_CONTROLLER.getOrCreateCategoryId();
        CourseRequest update = courseBuilder().description("Новое описание").price(new BigDecimal("99.90")).capacity(25)
                .level("ADVANCED").categoryId(categoryId).active(true).hiddenFromCatalog(true).sequential(true).build();

        Response response = COURSE_CONTROLLER.updateCourseResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        CourseResponse fromGet = COURSE_CONTROLLER.getCourseById(created.getId());
        assertAll(
                () -> assertEquals(update.getName(), response.as(CourseResponse.class).getName()),
                () -> assertEquals(update.getName(), fromGet.getName()),
                () -> assertEquals(update.getCode(), fromGet.getCode()),
                () -> assertEquals("Новое описание", fromGet.getDescription()),
                () -> assertEquals(0, new BigDecimal("99.90").compareTo(fromGet.getPrice())),
                () -> assertEquals(25, fromGet.getCapacity()),
                () -> assertEquals("ADVANCED", fromGet.getLevel()),
                () -> assertEquals(categoryId, fromGet.getCategoryId()),
                () -> assertTrue(fromGet.isHiddenFromCatalog()),
                () -> assertTrue(fromGet.isSequential()),
                () -> assertEquals(created.getCreatedBy(), fromGet.getCreatedBy(), "createdBy не должен меняться")
        );
    }

    @Test
    @DisplayName("PUT заменяет курс целиком: поля, которых нет в запросе, обнуляются")
    void updateCourseReplacesWholeObject() {
        CourseResponse created = createCourse(courseBuilder().description("Было описание").capacity(10).build());

        COURSE_CONTROLLER.updateCourseResponse(created.getId(), CourseRequest.builder().name(created.getName()).build());

        CourseResponse fromGet = COURSE_CONTROLLER.getCourseById(created.getId());
        assertAll(
                () -> assertEquals(created.getName(), fromGet.getName()),
                () -> assertNull(fromGet.getDescription()),
                () -> assertNull(fromGet.getCode()),
                () -> assertNull(fromGet.getCapacity())
        );
    }

    @Test
    @DisplayName("Граничные значения: name 100, code 20, description 5000 -> 200")
    void updateCourseWithBoundaryValues() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.updateCourseResponse(created.getId(), CourseRequest.builder()
                .name("n".repeat(100)).code("c".repeat(20)).description("d".repeat(5000)).build());

        assertEquals(200, response.statusCode(), response.asString());
    }

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", CourseRequest.builder().build()),
                Arguments.of("name пустой", CourseRequest.builder().name("").build()),
                Arguments.of("name пробелы", CourseRequest.builder().name("   ").build()),
                Arguments.of("name 101 символ", CourseRequest.builder().name("n".repeat(101)).build()),
                Arguments.of("code 21 символ", courseBuilder().code("c".repeat(21)).build()),
                Arguments.of("description 5001 символ", courseBuilder().description("d".repeat(5001)).build()),
                // ПАДАЕТ (баг бэка): 200 вместо 400 — отрицательная вместимость принимается
                Arguments.of("capacity отрицательная", courseBuilder().capacity(-1).build()),
                // ПАДАЕТ (баг бэка): 200 вместо 400 — отрицательная цена принимается
                Arguments.of("price отрицательная", courseBuilder().price(new BigDecimal("-5")).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateCourseWithInvalidData(String caseName, CourseRequest request) {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.updateCourseResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий categoryId при обновлении -> 404")
    void updateCourseWithUnknownCategory() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.updateCourseResponse(created.getId(), courseBuilder().categoryId(UNKNOWN_ID).build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление несуществующего курса -> 404")
    void updateUnknownCourse() {
        Response response = COURSE_CONTROLLER.updateCourseResponse(UNKNOWN_ID, courseBuilder().build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с невалидным id -> 400")
    void updateCourseWithInvalidId() {
        Response response = COURSE_CONTROLLER.updateCourseRaw("abc", "{\"name\":\"x\"}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "not a json", "[]", "{\"name\":\"abc\",\"capacity\":\"many\"}", "{\"name\":\"abc\",\"active\":\"yes\"}"})
    @DisplayName("Обновление с некорректным телом -> 400")
    void updateCourseWithMalformedBody(String body) {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.updateCourseRaw(created.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- POST /api/courses/{id}/clone ----------

    @Tag("smoke")
    @Test
    @DisplayName("Клонирование -> 201, копия с новым id и пометкой (copy)")
    void cloneCourse() {
        CourseResponse original = createCourse(courseBuilder().description("desc").price(new BigDecimal("5")).build());

        Response response = COURSE_CONTROLLER.cloneCourseResponse(original.getId());
        assertEquals(201, response.statusCode(), response.asString());

        CourseResponse copy = response.as(CourseResponse.class);
        CREATED.add(copy.getId());
        assertAll(
                () -> assertNotEquals(original.getId(), copy.getId()),
                () -> assertEquals(original.getName() + " (copy)", copy.getName()),
                () -> assertEquals("desc", copy.getDescription()),
                () -> assertEquals(0, new BigDecimal("5").compareTo(copy.getPrice())),
                () -> assertEquals(200, COURSE_CONTROLLER.getCourseByIdResponse(copy.getId()).statusCode())
        );
    }

    @Test
    @DisplayName("Клонирование несуществующего курса -> 404")
    void cloneUnknownCourse() {
        Response response = COURSE_CONTROLLER.cloneCourseResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Клонирование с невалидным id -> 400")
    void cloneCourseWithInvalidId() {
        Response response = COURSE_CONTROLLER.cloneCourseResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE, GET /deleted, POST /restore, DELETE /permanent ----------

    @Tag("smoke")
    @Test
    @DisplayName("Мягкое удаление -> 204, курс недоступен по id и появляется в /deleted")
    void softDeleteCourse() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response delete = COURSE_CONTROLLER.deleteCourseResponse(created.getId());
        Response get = COURSE_CONTROLLER.getCourseByIdResponse(created.getId());
        CourseResponse[] deleted = COURSE_CONTROLLER.deletedCoursesResponse().as(CourseResponse[].class);

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, get.statusCode(), get.asString()),
                () -> assertTrue(Arrays.stream(deleted).anyMatch(c -> created.getId().equals(c.getId())),
                        "Курс не появился в корзине")
        );
    }

    @Test
    @DisplayName("Удалённого курса нет в общем списке")
    void deletedCourseIsNotInList() {
        CourseResponse created = createCourse(EntityManager.generateCourse());
        COURSE_CONTROLLER.deleteCourseResponse(created.getId());

        CourseResponse[] courses = COURSE_CONTROLLER.getCoursesResponse().as(CourseResponse[].class);

        assertTrue(Arrays.stream(courses).noneMatch(c -> created.getId().equals(c.getId())));
    }

    @Test
    @DisplayName("Повторное мягкое удаление -> 404")
    void softDeleteTwice() {
        CourseResponse created = createCourse(EntityManager.generateCourse());
        COURSE_CONTROLLER.deleteCourseResponse(created.getId());

        Response response = COURSE_CONTROLLER.deleteCourseResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление несуществующего курса -> 404")
    void deleteUnknownCourse() {
        Response response = COURSE_CONTROLLER.deleteCourseResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление с невалидным id -> 400")
    void deleteCourseWithInvalidId() {
        Response response = COURSE_CONTROLLER.deleteCourseResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Tag("smoke")
    @Test
    @DisplayName("Восстановление -> 200, курс снова доступен и пропадает из /deleted")
    void restoreCourse() {
        CourseResponse created = createCourse(EntityManager.generateCourse());
        COURSE_CONTROLLER.deleteCourseResponse(created.getId());

        Response restore = COURSE_CONTROLLER.restoreCourseResponse(created.getId());
        Response get = COURSE_CONTROLLER.getCourseByIdResponse(created.getId());
        CourseResponse[] deleted = COURSE_CONTROLLER.deletedCoursesResponse().as(CourseResponse[].class);

        assertAll(
                () -> assertEquals(200, restore.statusCode(), restore.asString()),
                () -> assertEquals(created.getId(), restore.as(CourseResponse.class).getId()),
                () -> assertEquals(200, get.statusCode(), get.asString()),
                () -> assertTrue(Arrays.stream(deleted).noneMatch(c -> created.getId().equals(c.getId())))
        );
    }

    @Test
    @DisplayName("Восстановление не удалённого курса -> 404")
    void restoreCourseThatIsNotDeleted() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.restoreCourseResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Восстановление несуществующего курса -> 404")
    void restoreUnknownCourse() {
        Response response = COURSE_CONTROLLER.restoreCourseResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Восстановление с невалидным id -> 400")
    void restoreCourseWithInvalidId() {
        Response response = COURSE_CONTROLLER.restoreCourseResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Tag("smoke")
    @Test
    @DisplayName("Окончательное удаление после мягкого -> 204, курса нет нигде")
    void permanentDelete() {
        CourseResponse created = createCourse(EntityManager.generateCourse());
        COURSE_CONTROLLER.deleteCourseResponse(created.getId());

        Response delete = COURSE_CONTROLLER.deleteCoursePermanentlyResponse(created.getId());
        CourseResponse[] deleted = COURSE_CONTROLLER.deletedCoursesResponse().as(CourseResponse[].class);

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, COURSE_CONTROLLER.getCourseByIdResponse(created.getId()).statusCode()),
                () -> assertTrue(Arrays.stream(deleted).noneMatch(c -> created.getId().equals(c.getId()))),
                () -> assertEquals(404, COURSE_CONTROLLER.restoreCourseResponse(created.getId()).statusCode())
        );
    }

    @Test
    @DisplayName("Окончательное удаление курса, который не в корзине -> 404")
    void permanentDeleteWithoutSoftDelete() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.deleteCoursePermanentlyResponse(created.getId());

        assertAll(
                () -> assertEquals(404, response.statusCode(), response.asString()),
                () -> assertEquals(200, COURSE_CONTROLLER.getCourseByIdResponse(created.getId()).statusCode(), "Курс не должен удалиться")
        );
    }

    @Test
    @DisplayName("Окончательное удаление несуществующего курса -> 404")
    void permanentDeleteUnknownCourse() {
        Response response = COURSE_CONTROLLER.deleteCoursePermanentlyResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Окончательное удаление с невалидным id -> 400")
    void permanentDeleteWithInvalidId() {
        Response response = COURSE_CONTROLLER.deleteCoursePermanentlyResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/courses/{id}/files ----------

    @Test
    @DisplayName("GET /api/courses/{id}/files у курса без файлов -> 200 и пустой список")
    void courseFilesAreEmpty() {
        CourseResponse created = createCourse(EntityManager.generateCourse());

        Response response = COURSE_CONTROLLER.courseFilesResponse(created.getId());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("$").size())
        );
    }

    @Test
    @DisplayName("Файлы несуществующего курса -> 404")
    void courseFilesOfUnknownCourse() {
        Response response = COURSE_CONTROLLER.courseFilesResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Файлы курса с невалидным id -> 400")
    void courseFilesWithInvalidId() {
        Response response = COURSE_CONTROLLER.courseFilesResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }
}
