import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AnalyticsController;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.response.ActivityEventResponse;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.TimelinePageResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import kg.jazzlms.utils.Waiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Analytics service: лента событий и экспорт (/api/analytics/timeline)")
public class AnalyticsTimelineTest {

    private final AnalyticsController analytics = new AnalyticsController(Endpoints.ANALYTICS_URL);
    private final UserController userController = new UserController(Endpoints.BASE_URL);
    private final CourseController courseController = new CourseController(Endpoints.COURSE_URL);
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    // ---------- страницы ----------

    @Tag("smoke")
    @Test
    @DisplayName("Лента без параметров -> 200, page = 0, size = 10, событий не больше size")
    void timelineDefaults() {
        Response response = analytics.timelineResponse();
        assertEquals(200, response.statusCode(), response.asString());

        TimelinePageResponse page = response.as(TimelinePageResponse.class);
        assertAll(
                () -> assertEquals(0, page.getPage()),
                () -> assertEquals(10, page.getSize()),
                () -> assertFalse(page.getItems().isEmpty()),
                () -> assertTrue(page.getItems().size() <= 10),
                () -> assertTrue(page.getTotal() >= page.getItems().size())
        );
    }

    @Test
    @DisplayName("У события заполнены id, eventId, type, source, occurredAt, description")
    void eventHasRequiredFields() {
        ActivityEventResponse event = analytics.timeline(Map.of("size", 1)).getItems().get(0);

        assertAll(
                () -> assertNotNull(event.getId()),
                () -> assertNotNull(event.getEventId()),
                () -> assertNotNull(event.getType()),
                () -> assertNotNull(event.getSource()),
                () -> assertNotNull(event.getOccurredAt()),
                () -> assertNotNull(event.getDescription())
        );
    }

    @Test
    @DisplayName("size = 3 -> не больше 3 событий, эхо size в ответе")
    void timelineWithSize() {
        TimelinePageResponse page = analytics.timeline(Map.of("size", 3));

        assertAll(
                () -> assertEquals(3, page.getSize()),
                () -> assertEquals(3, page.getItems().size())
        );
    }

    @Test
    @DisplayName("Страница 1 не пересекается со страницей 0")
    void pagesDoNotOverlap() {
        TimelinePageResponse first = analytics.timeline(Map.of("size", 3, "page", 0));
        TimelinePageResponse second = analytics.timeline(Map.of("size", 3, "page", 1));

        List<String> firstIds = first.getItems().stream().map(ActivityEventResponse::getId).toList();
        assertAll(
                () -> assertEquals(1, second.getPage()),
                () -> assertTrue(second.getItems().stream().noneMatch(e -> firstIds.contains(e.getId())))
        );
    }

    @Test
    @DisplayName("События идут от новых к старым")
    void eventsAreSortedByTimeDescending() {
        List<ActivityEventResponse> items = analytics.timeline(Map.of("size", 20)).getItems();

        for (int i = 1; i < items.size(); i++) {
            assertTrue(items.get(i - 1).getOccurredAt().compareTo(items.get(i).getOccurredAt()) >= 0,
                    "Нарушен порядок на позиции " + i);
        }
    }

    @Test
    @DisplayName("Страница за пределами данных -> 200 и пустой items")
    void pageBeyondData() {
        TimelinePageResponse page = analytics.timeline(Map.of("page", 100000, "size", 10));

        assertTrue(page.getItems().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"size=0", "size=-1", "page=-1"})
    @DisplayName("Недопустимые page/size -> 400")
    void invalidPaging(String param) {
        String[] kv = param.split("=");

        Response response = analytics.timelineResponse(Map.of(kv[0], kv[1]));

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"page=abc", "size=abc", "from=bad", "to=bad", "type=BAD"})
    @DisplayName("Параметры неверного формата -> 400")
    void malformedParams(String param) {
        String[] kv = param.split("=");

        Response response = analytics.timelineResponse(Map.of(kv[0], kv[1]));

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- фильтры ----------

    @Tag("smoke")
    @Test
    @DisplayName("Фильтр type оставляет только события этого типа")
    void filterByType() {
        TimelinePageResponse page = analytics.timeline(Map.of("type", "USER_CREATED", "size", 20));

        assertAll(
                () -> assertFalse(page.getItems().isEmpty()),
                () -> assertTrue(page.getItems().stream().allMatch(e -> "USER_CREATED".equals(e.getType())))
        );
    }

    @Test
    @DisplayName("from в будущем -> пустая лента")
    void filterFromFuture() {
        TimelinePageResponse page = analytics.timeline(Map.of("from", "2100-01-01"));

        assertAll(
                () -> assertTrue(page.getItems().isEmpty()),
                () -> assertEquals(0, page.getTotal())
        );
    }

    @Test
    @DisplayName("to в далёком прошлом -> пустая лента")
    void filterToPast() {
        TimelinePageResponse page = analytics.timeline(Map.of("to", "2000-01-01"));

        assertEquals(0, page.getTotal());
    }

    @Test
    @DisplayName("Диапазон from..to включает сегодняшние события")
    void filterByTodayRange() {
        LocalDate today = LocalDate.now();

        TimelinePageResponse page = analytics.timeline(Map.of("from", today.minusDays(1).toString(), "to", today.plusDays(1).toString()));

        assertTrue(page.getTotal() > 0, "За сегодня должны быть события");
    }

    @Test
    @DisplayName("Неизвестный userId -> пустая лента")
    void filterByUnknownUser() {
        TimelinePageResponse page = analytics.timeline(Map.of("userId", UNKNOWN_ID));

        assertEquals(0, page.getTotal());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"userId=abc", "courseId=abc"})
    @DisplayName("userId/courseId не UUID -> 400")
    void filterWithInvalidId(String param) {
        String[] kv = param.split("=");

        // ПАДАЕТ (баг бэка): 200 и пустая лента вместо 400 — в отличие от page/from/type, формат id не проверяется
        Response response = analytics.timelineResponse(Map.of(kv[0], kv[1]));

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- события из других сервисов ----------

    @Tag("smoke")
    @Test
    @DisplayName("Создание пользователя попадает в ленту как USER_CREATED с данными пользователя")
    void userCreationAppearsInTimeline() {
        UserResponse user = userController.createUser(EntityManager.generateUser());
        try {
            boolean appeared = Waiter.waitUntil(15, () -> !analytics.timeline(Map.of("userId", user.getId().toString(), "type", "USER_CREATED")).getItems().isEmpty());
            assertTrue(appeared, "Событие USER_CREATED не появилось в ленте за 15 секунд");

            ActivityEventResponse event = analytics.timeline(Map.of("userId", user.getId().toString(), "type", "USER_CREATED")).getItems().get(0);
            assertAll(
                    () -> assertEquals("user-service", event.getSource()),
                    () -> assertEquals(user.getId().toString(), event.getUserId()),
                    () -> assertEquals(user.getUsername(), event.getPayload().get("username")),
                    () -> assertEquals(user.getEmail(), event.getPayload().get("email"))
            );
        } finally {
            userController.deleteUserResponse(user.getId());
        }
    }

    @Test
    @DisplayName("Удаление пользователя попадает в ленту как USER_DELETED")
    void userDeletionAppearsInTimeline() {
        UserResponse user = userController.createUser(EntityManager.generateUser());
        userController.deleteUserResponse(user.getId());

        boolean appeared = Waiter.waitUntil(15, () -> !analytics.timeline(Map.of("userId", user.getId().toString(), "type", "USER_DELETED")).getItems().isEmpty());

        assertTrue(appeared, "Событие USER_DELETED не появилось в ленте за 15 секунд");
    }

    @Test
    @DisplayName("Создание курса попадает в ленту как COURSE_CREATED, фильтр courseId находит его")
    void courseCreationAppearsInTimeline() {
        CourseResponse course = courseController.createCourse(EntityManager.generateCourse());
        try {
            boolean appeared = Waiter.waitUntil(15, () -> !analytics.timeline(Map.of("courseId", course.getId().toString(), "type", "COURSE_CREATED")).getItems().isEmpty());

            assertTrue(appeared, "Событие COURSE_CREATED не появилось в ленте за 15 секунд");
        } finally {
            courseController.deleteCourseResponse(course.getId());
            courseController.deleteCoursePermanentlyResponse(course.getId());
        }
    }

    // ---------- GET /api/analytics/timeline/export ----------

    @Tag("smoke")
    @Test
    @DisplayName("Экспорт -> 200, CSV с заголовком date,event,user,course,description")
    void export() {
        Response response = analytics.timelineExportResponse();
        assertEquals(200, response.statusCode(), response.asString());

        String firstLine = response.asString().replace("﻿", "").lines().findFirst().orElse("");
        assertAll(
                () -> assertEquals("date,event,user,course,description", firstLine),
                () -> assertTrue(response.asString().lines().count() > 1, "В экспорте только заголовок")
        );
    }

    @Test
    @DisplayName("Экспорт с type оставляет только события этого типа")
    void exportFilteredByType() {
        Response response = analytics.timelineExportResponse(Map.of("type", "USER_CREATED"));

        List<String> rows = response.asString().lines().skip(1).filter(l -> !l.isBlank()).toList();
        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertFalse(rows.isEmpty()),
                () -> assertTrue(rows.stream().allMatch(r -> r.contains("\"USER_CREATED\"")))
        );
    }

    @Test
    @DisplayName("Экспорт по неизвестному userId -> только заголовок")
    void exportForUnknownUser() {
        Response response = analytics.timelineExportResponse(Map.of("userId", UNKNOWN_ID));

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(1, response.asString().lines().filter(l -> !l.isBlank()).count())
        );
    }

    @Test
    @DisplayName("Экспорт с неверным type -> 400")
    void exportWithInvalidType() {
        Response response = analytics.timelineExportResponse(Map.of("type", "BAD"));

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Экспорт с userId не UUID -> 400")
    void exportWithInvalidUserId() {
        // ПАДАЕТ (баг бэка): 200 вместо 400 — формат userId не проверяется
        Response response = analytics.timelineExportResponse(Map.of("userId", "abc"));

        assertEquals(400, response.statusCode(), response.asString());
    }
}
