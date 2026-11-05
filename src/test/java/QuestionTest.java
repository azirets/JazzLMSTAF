import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.QuestionController;
import kg.jazzlms.models.request.QuestionRequest;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.QuestionResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static kg.jazzlms.utils.EntityManager.questionBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
@DisplayName("Course service: банк вопросов (/api/courses/{id}/questions, /api/questions/{id})")
public class QuestionTest {

    private static final CourseController COURSE_CONTROLLER = new CourseController(Endpoints.COURSE_URL);
    private static final QuestionController QUESTION_CONTROLLER = new QuestionController(Endpoints.COURSE_URL);
    private static final List<UUID> COURSES = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String IMPORT_TEXT = "What is 2+2?\nA. 3\nB. 4\nANSWER: B";

    private CourseResponse createCourse() {
        CourseResponse created = COURSE_CONTROLLER.createCourse(EntityManager.generateCourse());
        COURSES.add(created.getId());
        return created;
    }

    @AfterAll
    static void cleanUp() {
        COURSES.forEach(id -> {
            COURSE_CONTROLLER.deleteCourseResponse(id);
            COURSE_CONTROLLER.deleteCoursePermanentlyResponse(id);
        });
    }

    // ---------- POST /api/courses/{id}/questions: positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("MULTIPLE_CHOICE -> 201, поля совпадают, usedInTests = 0")
    void createMultipleChoiceQuestion() {
        CourseResponse course = createCourse();
        QuestionRequest request = questionBuilder().feedback("Подсказка").tags("geo").build();

        Response response = QUESTION_CONTROLLER.createQuestionResponse(course.getId(), request);
        assertEquals(201, response.statusCode(), response.asString());

        QuestionResponse created = response.as(QuestionResponse.class);
        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals(course.getId(), created.getCourseId()),
                () -> assertEquals(course.getName(), created.getCourseName()),
                () -> assertEquals("MULTIPLE_CHOICE", created.getType()),
                () -> assertEquals(request.getText(), created.getText()),
                () -> assertEquals("Подсказка", created.getFeedback()),
                () -> assertEquals("geo", created.getTags()),
                () -> assertEquals(0, created.getUsedInTests()),
                () -> assertNotNull(created.getCreatedAt())
        );
    }

    @Test
    @DisplayName("FILL_GAP -> 201")
    void createFillGapQuestion() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.createQuestionResponse(course.getId(),
                questionBuilder().type("FILL_GAP").text("2+2=[gap]").data(Map.of("gaps", List.of("4"))).build());

        assertEquals(201, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("ORDERING -> 201")
    void createOrderingQuestion() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.createQuestionResponse(course.getId(),
                questionBuilder().type("ORDERING").data(Map.of("items", List.of("a", "b", "c"))).build());

        assertEquals(201, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("DRAG_DROP -> 201")
    void createDragDropQuestion() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.createQuestionResponse(course.getId(),
                questionBuilder().type("DRAG_DROP").data(Map.of("pairs", List.of(
                        Map.of("left", "a", "right", "1"), Map.of("left", "b", "right", "2")))).build());

        assertEquals(201, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("RANDOMIZED из пула существующих вопросов -> 201")
    void createRandomizedQuestion() {
        CourseResponse course = createCourse();
        QuestionResponse pooled = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());

        Response response = QUESTION_CONTROLLER.createQuestionResponse(course.getId(),
                questionBuilder().type("RANDOMIZED").data(Map.of("pool", List.of(pooled.getId().toString()), "count", 1)).build());

        assertEquals(201, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Граничные значения: text 5000, feedback 2000, tags 255 -> 201")
    void createQuestionWithBoundaryValues() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.createQuestionResponse(course.getId(),
                questionBuilder().text("t".repeat(5000)).feedback("f".repeat(2000)).tags("g".repeat(255)).build());

        assertEquals(201, response.statusCode(), response.asString());
    }

    // ---------- POST: negative ----------

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("text = null", questionBuilder().text(null).build()),
                Arguments.of("text пустой", questionBuilder().text("").build()),
                Arguments.of("text пробелы", questionBuilder().text("   ").build()),
                Arguments.of("text 5001 символ", questionBuilder().text("t".repeat(5001)).build()),
                Arguments.of("type = null", questionBuilder().type(null).build()),
                Arguments.of("feedback 2001 символ", questionBuilder().feedback("f".repeat(2001)).build()),
                Arguments.of("tags 256 символов", questionBuilder().tags("g".repeat(256)).build()),
                Arguments.of("MULTIPLE_CHOICE без data", questionBuilder().data(null).build()),
                Arguments.of("MULTIPLE_CHOICE с одним ответом", questionBuilder().data(Map.of("answers",
                        List.of(Map.of("text", "Only", "correct", true)))).build()),
                Arguments.of("ORDERING с одним элементом", questionBuilder().type("ORDERING").data(Map.of("items", List.of("a"))).build()),
                Arguments.of("DRAG_DROP с одной парой", questionBuilder().type("DRAG_DROP").data(Map.of("pairs",
                        List.of(Map.of("left", "a", "right", "1")))).build()),
                Arguments.of("RANDOMIZED с пустым пулом", questionBuilder().type("RANDOMIZED").data(Map.of("pool", List.of(), "count", 1)).build()),
                Arguments.of("FREE_TEXT без ключевых слов", questionBuilder().type("FREE_TEXT").data(null).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createQuestionWithInvalidData(String caseName, QuestionRequest request) {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.createQuestionResponse(course.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "{}", "not a json", "[]", "{\"type\":\"BAD\",\"text\":\"x\"}", "{\"type\":\"MULTIPLE_CHOICE\",\"text\":\"x\",\"data\":\"str\"}"})
    @DisplayName("Некорректное тело -> 400")
    void createQuestionWithMalformedBody(String body) {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.createQuestionRaw(course.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Вопрос в несуществующем курсе -> 404, с невалидным id курса -> 400")
    void createQuestionInWrongCourse() {
        Response unknown = QUESTION_CONTROLLER.createQuestionResponse(UNKNOWN_ID, questionBuilder().build());
        Response invalid = QUESTION_CONTROLLER.createQuestionResponse("abc", questionBuilder().build());

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- GET /api/courses/{id}/questions ----------

    @Tag("smoke")
    @Test
    @DisplayName("Список вопросов курса -> 200, только вопросы этого курса")
    void getQuestionsOfCourse() {
        CourseResponse course = createCourse();
        CourseResponse other = createCourse();
        QuestionResponse mine = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        QuestionResponse foreign = QUESTION_CONTROLLER.createQuestion(other.getId(), questionBuilder().build());

        Response response = QUESTION_CONTROLLER.getQuestionsResponse(course.getId());
        assertEquals(200, response.statusCode(), response.asString());

        QuestionResponse[] questions = response.as(QuestionResponse[].class);
        assertAll(
                () -> assertTrue(Arrays.stream(questions).anyMatch(q -> mine.getId().equals(q.getId()))),
                () -> assertTrue(Arrays.stream(questions).noneMatch(q -> foreign.getId().equals(q.getId())))
        );
    }

    @Test
    @DisplayName("all=true добавляет вопросы других курсов")
    void getQuestionsWithAllFlag() {
        CourseResponse course = createCourse();
        CourseResponse other = createCourse();
        QuestionResponse foreign = QUESTION_CONTROLLER.createQuestion(other.getId(), questionBuilder().build());

        QuestionResponse[] questions = QUESTION_CONTROLLER.getQuestionsResponse(course.getId(), true, null).as(QuestionResponse[].class);

        assertTrue(Arrays.stream(questions).anyMatch(q -> foreign.getId().equals(q.getId())));
    }

    @Test
    @DisplayName("search по тексту находит вопрос, без совпадений -> пустой список")
    void searchQuestions() {
        CourseResponse course = createCourse();
        String token = "uniq" + UUID.randomUUID().toString().substring(0, 8);
        QuestionResponse created = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().text("Question " + token).build());

        QuestionResponse[] found = QUESTION_CONTROLLER.getQuestionsResponse(course.getId(), null, token).as(QuestionResponse[].class);
        QuestionResponse[] none = QUESTION_CONTROLLER.getQuestionsResponse(course.getId(), null, "nomatch" + token).as(QuestionResponse[].class);

        assertAll(
                () -> assertEquals(1, found.length),
                () -> assertEquals(created.getId(), found[0].getId()),
                () -> assertEquals(0, none.length)
        );
    }

    @Test
    @DisplayName("Список вопросов несуществующего курса -> 404, невалидного id -> 400")
    void getQuestionsOfWrongCourse() {
        Response unknown = QUESTION_CONTROLLER.getQuestionsResponse(UNKNOWN_ID);
        Response invalid = QUESTION_CONTROLLER.getQuestionsResponse("abc");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- GET /api/questions/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /questions/{id} -> 200, данные совпадают")
    void getQuestionById() {
        CourseResponse course = createCourse();
        QuestionResponse created = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());

        Response response = QUESTION_CONTROLLER.getQuestionResponse(created.getId());
        assertEquals(200, response.statusCode(), response.asString());

        QuestionResponse actual = response.as(QuestionResponse.class);
        assertAll(
                () -> assertEquals(created.getId(), actual.getId()),
                () -> assertEquals(created.getText(), actual.getText()),
                () -> assertEquals(created.getData(), actual.getData())
        );
    }

    @Test
    @DisplayName("Несуществующий вопрос -> 404, невалидный id -> 400")
    void getUnknownQuestion() {
        Response unknown = QUESTION_CONTROLLER.getQuestionResponse(UNKNOWN_ID);
        Response invalid = QUESTION_CONTROLLER.getQuestionResponse("abc");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- PUT /api/questions/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление -> 200, GET показывает новые значения")
    void updateQuestion() {
        CourseResponse course = createCourse();
        QuestionResponse created = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        QuestionRequest update = questionBuilder().feedback("Новая подсказка").tags("new").build();

        Response response = QUESTION_CONTROLLER.updateQuestionResponse(created.getId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        QuestionResponse fromGet = QUESTION_CONTROLLER.getQuestionResponse(created.getId()).as(QuestionResponse.class);
        assertAll(
                () -> assertEquals(update.getText(), response.as(QuestionResponse.class).getText()),
                () -> assertEquals(update.getText(), fromGet.getText()),
                () -> assertEquals("Новая подсказка", fromGet.getFeedback()),
                () -> assertEquals("new", fromGet.getTags())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateQuestionWithInvalidData(String caseName, QuestionRequest request) {
        CourseResponse course = createCourse();
        QuestionResponse created = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());

        Response response = QUESTION_CONTROLLER.updateQuestionResponse(created.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление несуществующего вопроса -> 404, невалидного id -> 400")
    void updateWrongQuestion() {
        Response unknown = QUESTION_CONTROLLER.updateQuestionResponse(UNKNOWN_ID, questionBuilder().build());
        Response invalid = QUESTION_CONTROLLER.updateQuestionRaw("abc", "{}");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- DELETE /api/questions/{id} ----------

    @Tag("smoke")
    @Test
    @DisplayName("Удаление -> 204, дальше GET -> 404")
    void deleteQuestion() {
        CourseResponse course = createCourse();
        QuestionResponse created = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());

        Response delete = QUESTION_CONTROLLER.deleteQuestionResponse(created.getId());
        Response get = QUESTION_CONTROLLER.getQuestionResponse(created.getId());

        assertAll(
                () -> assertEquals(204, delete.statusCode(), delete.asString()),
                () -> assertEquals(404, get.statusCode(), get.asString())
        );
    }

    @Test
    @DisplayName("Повторное удаление -> 404, несуществующий -> 404, невалидный id -> 400")
    void deleteQuestionNegative() {
        CourseResponse course = createCourse();
        QuestionResponse created = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        QUESTION_CONTROLLER.deleteQuestionResponse(created.getId());

        Response twice = QUESTION_CONTROLLER.deleteQuestionResponse(created.getId());
        Response unknown = QUESTION_CONTROLLER.deleteQuestionResponse(UNKNOWN_ID);
        Response invalid = QUESTION_CONTROLLER.deleteQuestionResponse("abc");

        assertAll(
                () -> assertEquals(404, twice.statusCode(), twice.asString()),
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- POST /api/courses/{id}/questions/import ----------

    private static String importBody(String text) {
        return kg.jazzlms.utils.JsonUtils.toJson(Map.of("data", text));
    }

    @Tag("smoke")
    @Test
    @DisplayName("Импорт текста с ANSWER -> 200, вопрос с верным вариантом создан и помечен imported")
    void importQuestions() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.importResponse(course.getId(), importBody(IMPORT_TEXT), false);
        assertEquals(200, response.statusCode(), response.asString());

        QuestionResponse[] imported = response.as(QuestionResponse[].class);
        QuestionResponse[] saved = QUESTION_CONTROLLER.getQuestionsResponse(course.getId()).as(QuestionResponse[].class);
        assertAll(
                () -> assertEquals(1, imported.length),
                () -> assertEquals("What is 2+2?", imported[0].getText()),
                () -> assertEquals("MULTIPLE_CHOICE", imported[0].getType()),
                () -> assertEquals("imported", imported[0].getTags()),
                () -> assertEquals(1, saved.length, "Вопрос должен сохраниться")
        );
    }

    @Test
    @DisplayName("Импорт без параметра dryRun сохраняет вопросы")
    void importWithoutDryRunSaves() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.importResponse(course.getId(), importBody(IMPORT_TEXT), null);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(1, QUESTION_CONTROLLER.getQuestionsResponse(course.getId()).as(QuestionResponse[].class).length)
        );
    }

    @Test
    @DisplayName("dryRun=true -> 200, вопросы разобраны, но не сохранены")
    void importDryRunDoesNotSave() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.importResponse(course.getId(), importBody(IMPORT_TEXT), true);
        assertEquals(200, response.statusCode(), response.asString());

        assertAll(
                () -> assertEquals(1, response.jsonPath().getList("$").size()),
                () -> assertEquals("What is 2+2?", response.jsonPath().getString("[0].text")),
                () -> assertEquals(0, QUESTION_CONTROLLER.getQuestionsResponse(course.getId()).as(QuestionResponse[].class).length)
        );
    }

    @Test
    @DisplayName("Импорт нескольких вопросов за раз")
    void importSeveralQuestions() {
        CourseResponse course = createCourse();
        String text = IMPORT_TEXT + "\n\nCapital of France?\nA. Paris\nB. Rome\nANSWER: A";

        Response response = QUESTION_CONTROLLER.importResponse(course.getId(), importBody(text), false);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(2, response.jsonPath().getList("$").size())
        );
    }

    @ParameterizedTest(name = "data = ''{0}''")
    @ValueSource(strings = {"", "   ", "Just a line without answer", "ANSWER: A"})
    @DisplayName("Пустой или непонятный текст -> 400")
    void importInvalidText(String text) {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.importResponse(course.getId(), importBody(text), true);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "{}", "not a json", "[]"})
    @DisplayName("Импорт с некорректным телом -> 400")
    void importWithMalformedBody(String body) {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.importResponse(course.getId(), body, true);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("dryRun не булево значение -> 400")
    void importWithInvalidDryRun() {
        CourseResponse course = createCourse();

        Response response = QUESTION_CONTROLLER.importResponse(course.getId(), importBody(IMPORT_TEXT), "maybe");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Импорт в несуществующий курс -> 404, с невалидным id -> 400")
    void importIntoWrongCourse() {
        Response unknown = QUESTION_CONTROLLER.importResponse(UNKNOWN_ID, importBody(IMPORT_TEXT), true);
        Response invalid = QUESTION_CONTROLLER.importResponse("abc", importBody(IMPORT_TEXT), true);

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }
}
