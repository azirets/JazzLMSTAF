import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CategoryController;
import kg.jazzlms.models.request.CategoryRequest;
import kg.jazzlms.models.response.CategoryResponse;
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

import static kg.jazzlms.utils.EntityManager.categoryBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Course service: категории (/api/categories)")
public class CategoryTest {

    private static final CategoryController CATEGORY_CONTROLLER = new CategoryController(Endpoints.COURSE_URL);
    private static final List<UUID> CREATED = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private CategoryResponse createCategory(CategoryRequest request) {
        CategoryResponse created = CATEGORY_CONTROLLER.createCategory(request);
        CREATED.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        CREATED.forEach(CATEGORY_CONTROLLER::deleteCategoryResponse);
    }

    // ---------- POST /api/categories ----------

    @Tag("smoke")
    @Test
    @DisplayName("Только name -> 201, parentId и price = null")
    void createCategoryWithOnlyName() {
        CategoryRequest request = categoryBuilder().build();

        CategoryResponse created = createCategory(request);

        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertNull(created.getParentId()),
                () -> assertNull(created.getPrice())
        );
    }

    @Test
    @DisplayName("С ценой и родителем -> 201")
    void createCategoryWithParentAndPrice() {
        CategoryResponse parent = createCategory(categoryBuilder().build());

        CategoryResponse child = createCategory(categoryBuilder().parentId(parent.getId()).price(new BigDecimal("10.50")).build());

        assertAll(
                () -> assertEquals(parent.getId(), child.getParentId()),
                () -> assertEquals(0, new BigDecimal("10.50").compareTo(child.getPrice()))
        );
    }

    @Test
    @DisplayName("Граничное значение: name 100 символов -> 201")
    void createCategoryWithMaxName() {
        String name = "n".repeat(90) + UUID.randomUUID().toString().substring(0, 10);

        CategoryResponse created = createCategory(CategoryRequest.builder().name(name).build());

        assertEquals(name, created.getName());
    }

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", CategoryRequest.builder().build()),
                Arguments.of("name пустой", CategoryRequest.builder().name("").build()),
                Arguments.of("name пробелы", CategoryRequest.builder().name("   ").build()),
                Arguments.of("name 101 символ", CategoryRequest.builder().name("n".repeat(101)).build()),
                // ПАДАЕТ (баг бэка): 201 вместо 400 — отрицательная цена принимается
                Arguments.of("price отрицательный", categoryBuilder().price(new BigDecimal("-1")).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createCategoryWithInvalidData(String caseName, CategoryRequest request) {
        Response response = CATEGORY_CONTROLLER.createCategoryResponse(request);
        if (response.statusCode() == 201) {
            CREATED.add(response.as(CategoryResponse.class).getId());
        }

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Дубликат name -> 409")
    void createCategoryWithDuplicateName() {
        CategoryResponse first = createCategory(categoryBuilder().build());

        Response response = CATEGORY_CONTROLLER.createCategoryResponse(categoryBuilder().name(first.getName()).build());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий parentId -> 404")
    void createCategoryWithUnknownParent() {
        Response response = CATEGORY_CONTROLLER.createCategoryResponse(categoryBuilder().parentId(UNKNOWN_ID).build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "not a json", "{\"name\": \"abc\",", "[]", "{\"name\":\"abc\",\"price\":\"many\"}", "{\"name\":\"abc\",\"parentId\":\"abc\"}"})
    @DisplayName("Некорректное тело -> 400")
    void createCategoryWithMalformedBody(String body) {
        Response response = CATEGORY_CONTROLLER.createCategoryRaw(body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- GET /api/categories ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /api/categories -> 200, созданная категория в списке")
    void getAllCategories() {
        CategoryResponse created = createCategory(categoryBuilder().build());

        Response response = CATEGORY_CONTROLLER.getCategoriesResponse();
        assertEquals(200, response.statusCode(), response.asString());

        CategoryResponse[] categories = response.as(CategoryResponse[].class);
        assertTrue(Arrays.stream(categories).anyMatch(c -> created.getId().equals(c.getId())),
                "Созданная категория не найдена в списке");
    }

    // ---------- PUT /api/categories/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление -> 200, GET списка показывает новые значения")
    void updateCategory() {
        CategoryResponse created = createCategory(categoryBuilder().build());
        CategoryRequest update = categoryBuilder().price(new BigDecimal("20")).build();

        Response response = CATEGORY_CONTROLLER.updateCategoryResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        CategoryResponse updated = response.as(CategoryResponse.class);
        CategoryResponse fromList = Arrays.stream(CATEGORY_CONTROLLER.getCategoriesResponse().as(CategoryResponse[].class))
                .filter(c -> created.getId().equals(c.getId())).findFirst().orElseThrow();
        assertAll(
                () -> assertEquals(created.getId(), updated.getId()),
                () -> assertEquals(update.getName(), updated.getName()),
                () -> assertEquals(update.getName(), fromList.getName()),
                () -> assertEquals(0, new BigDecimal("20").compareTo(fromList.getPrice()))
        );
    }

    @Test
    @DisplayName("Смена родителя на существующую категорию -> 200")
    void updateCategoryParent() {
        CategoryResponse parent = createCategory(categoryBuilder().build());
        CategoryResponse child = createCategory(categoryBuilder().build());

        Response response = CATEGORY_CONTROLLER.updateCategoryResponse(child.getId(),
                categoryBuilder().name(child.getName()).parentId(parent.getId()).build());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(parent.getId(), response.as(CategoryResponse.class).getParentId())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateInvalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateCategoryWithInvalidData(String caseName, CategoryRequest request) {
        CategoryResponse created = createCategory(categoryBuilder().build());

        Response response = CATEGORY_CONTROLLER.updateCategoryResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    static Stream<Arguments> updateInvalidData() {
        return invalidData().filter(a -> !a.get()[0].toString().startsWith("price"));
    }

    @Test
    @DisplayName("Имя другой категории при обновлении -> 409")
    void updateCategoryWithDuplicateName() {
        CategoryResponse first = createCategory(categoryBuilder().build());
        CategoryResponse second = createCategory(categoryBuilder().build());

        Response response = CATEGORY_CONTROLLER.updateCategoryResponse(second.getId(),
                categoryBuilder().name(first.getName()).build());

        // ПАДАЕТ (баг бэка): 200 вместо 409 — при обновлении дубликат имени не проверяется
        assertEquals(409, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Категория сама себе родитель -> 400")
    void updateCategoryWithItselfAsParent() {
        CategoryResponse created = createCategory(categoryBuilder().build());

        Response response = CATEGORY_CONTROLLER.updateCategoryResponse(created.getId(),
                categoryBuilder().name(created.getName()).parentId(created.getId()).build());

        // ПАДАЕТ (баг бэка): 200 вместо 400 — категория становится родителем самой себя
        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление несуществующей категории -> 404")
    void updateUnknownCategory() {
        Response response = CATEGORY_CONTROLLER.updateCategoryResponse(UNKNOWN_ID, categoryBuilder().build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с невалидным id -> 400")
    void updateCategoryWithInvalidId() {
        Response response = CATEGORY_CONTROLLER.updateCategoryRaw("abc", "{\"name\":\"x\"}");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление с некорректным телом -> 400")
    void updateCategoryWithMalformedBody() {
        CategoryResponse created = createCategory(categoryBuilder().build());

        Response response = CATEGORY_CONTROLLER.updateCategoryRaw(created.getId(), "not a json");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- DELETE /api/categories/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление -> 204, категории больше нет в списке")
    void deleteCategory() {
        CategoryResponse created = CATEGORY_CONTROLLER.createCategory(categoryBuilder().build());

        Response delete = CATEGORY_CONTROLLER.deleteCategoryResponse(created.getId());

        CategoryResponse[] categories = CATEGORY_CONTROLLER.getCategoriesResponse().as(CategoryResponse[].class);
        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertTrue(Arrays.stream(categories).noneMatch(c -> created.getId().equals(c.getId())))
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404")
    void deleteCategoryTwice() {
        CategoryResponse created = CATEGORY_CONTROLLER.createCategory(categoryBuilder().build());
        CATEGORY_CONTROLLER.deleteCategoryResponse(created.getId());

        Response response = CATEGORY_CONTROLLER.deleteCategoryResponse(created.getId());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление несуществующей категории -> 404")
    void deleteUnknownCategory() {
        Response response = CATEGORY_CONTROLLER.deleteCategoryResponse(UNKNOWN_ID);

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Удаление с невалидным id -> 400")
    void deleteCategoryWithInvalidId() {
        Response response = CATEGORY_CONTROLLER.deleteCategoryResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }
}
