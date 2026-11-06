import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.AnalyticsController;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.CourseRequest;
import kg.jazzlms.models.response.ActivityEventResponse;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.TimelinePageResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.models.response.UserStatsResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки общего состояния сервисов: счётчики ("стало ровно на 1 больше") и страницы ленты событий.
 * Остальные тесты идут параллельно и постоянно создают и удаляют пользователей и курсы, поэтому глобальный
 * счётчик и лента в это время не стоят на месте (страницы сдвигаются). @Isolated запускает этот класс одним, пока остальные ждут.
 */
@Isolated
@Tag("regression")
@DisplayName("Общее состояние сервисов: счётчики и постраничная лента (тесты идут по одному)")
public class IsolatedGlobalStateTest {

    private final UserController userController = new UserController(Endpoints.BASE_URL);
    private final CourseController courseController = new CourseController(Endpoints.COURSE_URL);

    @Test
    @DisplayName("User service: после создания активного пользователя total и active растут на 1")
    void userStatsGrowAfterCreate() {
        UserStatsResponse before = userController.statsResponse().as(UserStatsResponse.class);
        UserResponse created = userController.createUser(EntityManager.userBuilder().active(true).build());
        try {
            UserStatsResponse after = userController.statsResponse().as(UserStatsResponse.class);
            assertAll(
                    () -> assertEquals(before.getTotal() + 1, after.getTotal(), "total"),
                    () -> assertEquals(before.getActive() + 1, after.getActive(), "active")
            );
        } finally {
            userController.deleteUserResponse(created.getId());
        }
    }

    @Test
    @DisplayName("Course service: после создания активного курса activeCourses растёт на 1")
    void courseStatsGrowAfterCreate() {
        int before = courseController.courseStatsResponse().jsonPath().getInt("activeCourses");
        CourseRequest request = EntityManager.generateCourse();
        CourseResponse created = courseController.createCourse(request);
        try {
            int after = courseController.courseStatsResponse().jsonPath().getInt("activeCourses");
            assertEquals(before + 1, after);
        } finally {
            courseController.deleteCourseResponse(created.getId());
            courseController.deleteCoursePermanentlyResponse(created.getId());
        }
    }

    @Test
    @DisplayName("Analytics service: страница 1 ленты не пересекается со страницей 0")
    void timelinePagesDoNotOverlap() {
        AnalyticsController analytics = new AnalyticsController(Endpoints.ANALYTICS_URL);

        TimelinePageResponse first = analytics.timeline(Map.of("size", 3, "page", 0));
        TimelinePageResponse second = analytics.timeline(Map.of("size", 3, "page", 1));

        List<String> firstIds = first.getItems().stream().map(ActivityEventResponse::getId).toList();
        assertAll(
                () -> assertEquals(1, second.getPage()),
                () -> assertTrue(second.getItems().stream().noneMatch(e -> firstIds.contains(e.getId())),
                        "Страницы пересеклись: " + firstIds + " и " + second.getItems().stream().map(ActivityEventResponse::getId).toList())
        );
    }
}
