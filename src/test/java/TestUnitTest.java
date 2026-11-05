import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.QuestionController;
import kg.jazzlms.controller.UnitController;
import kg.jazzlms.models.request.TestRequest;
import kg.jazzlms.models.response.AttemptResponse;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.QuestionResponse;
import kg.jazzlms.models.response.TestResponse;
import kg.jazzlms.models.response.UnitResponse;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static kg.jazzlms.utils.EntityManager.questionBuilder;
import static kg.jazzlms.utils.EntityManager.testBuilder;
import static kg.jazzlms.utils.EntityManager.unitBuilder;
import static org.junit.jupiter.api.Assertions.*;

/** Тесты как юниты курса: создание и настройка, попытки прохождения */
@Tag("regression")
@DisplayName("Course service: тесты и попытки прохождения")
public class TestUnitTest {

    private static final CourseController COURSE_CONTROLLER = new CourseController(Endpoints.COURSE_URL);
    private static final UnitController UNIT_CONTROLLER = new UnitController(Endpoints.COURSE_URL);
    private static final QuestionController QUESTION_CONTROLLER = new QuestionController(Endpoints.COURSE_URL);
    private static final List<UUID> COURSES = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private CourseResponse createCourse() {
        CourseResponse created = COURSE_CONTROLLER.createCourse(EntityManager.generateCourse());
        COURSES.add(created.getId());
        return created;
    }

    private TestResponse createTest(CourseResponse course, TestRequest request) {
        return UNIT_CONTROLLER.createTest(course.getId(), request);
    }

    private static TestRequest.Item item(QuestionResponse question, int weight) {
        return new TestRequest.Item(question.getId(), weight);
    }

    /** Тест с одним вопросом (правильный вариант — id 0), повторные попытки разрешены всегда */
    private TestResponse createTestWithQuestion(CourseResponse course, QuestionResponse question) {
        return createTest(course, testBuilder().passScore(50).repetitions("ALWAYS").questions(List.of(item(question, 1))).build());
    }

    @AfterAll
    static void cleanUp() {
        COURSES.forEach(id -> {
            COURSE_CONTROLLER.deleteCourseResponse(id);
            COURSE_CONTROLLER.deleteCoursePermanentlyResponse(id);
        });
    }

    // ---------- POST /api/courses/{id}/tests: positive ----------

    @Tag("smoke")
    @Test
    @DisplayName("Только name -> 201, значения по умолчанию")
    void createTestWithOnlyName() {
        CourseResponse course = createCourse();
        TestRequest request = testBuilder().build();

        TestResponse created = createTest(course, request);

        assertAll(
                () -> assertNotNull(created.getUnitId()),
                () -> assertEquals(course.getId(), created.getCourseId()),
                () -> assertEquals(request.getName(), created.getName()),
                () -> assertTrue(created.isActive()),
                () -> assertEquals(50, created.getPassScore()),
                () -> assertNull(created.getDurationMinutes()),
                () -> assertNull(created.getMaxAttempts()),
                () -> assertEquals("IF_NOT_PASSED", created.getRepetitions()),
                () -> assertEquals("WHEN_PASSED", created.getShowCorrectAnswers()),
                () -> assertEquals(0, created.getQuestionCount())
        );
    }

    @Test
    @DisplayName("Все поля заполнены -> 201, ответ совпадает с запросом")
    void createTestWithAllFields() {
        CourseResponse course = createCourse();
        TestRequest request = testBuilder().active(true).durationMinutes(30).passScore(80).shuffleQuestions(true)
                .shuffleAnswers(true).repetitions("NEVER").maxAttempts(3).showCorrectAnswers("ALWAYS")
                .showGivenAnswers(false).showLabels(false).showScore(false).description("Описание")
                .messagePassed("Сдал").messageFailed("Не сдал").build();

        TestResponse created = createTest(course, request);

        assertAll(
                () -> assertEquals(30, created.getDurationMinutes()),
                () -> assertEquals(80, created.getPassScore()),
                () -> assertTrue(created.isShuffleQuestions()),
                () -> assertTrue(created.isShuffleAnswers()),
                () -> assertEquals("NEVER", created.getRepetitions()),
                () -> assertEquals(3, created.getMaxAttempts()),
                () -> assertEquals("ALWAYS", created.getShowCorrectAnswers()),
                () -> assertFalse(created.isShowGivenAnswers()),
                () -> assertFalse(created.isShowLabels()),
                () -> assertFalse(created.isShowScore()),
                () -> assertEquals("Описание", created.getDescription()),
                () -> assertEquals("Сдал", created.getMessagePassed()),
                () -> assertEquals("Не сдал", created.getMessageFailed())
        );
    }

    @Test
    @DisplayName("С вопросами: questionCount и totalWeight считаются по весам, usedInTests растёт")
    void createTestWithQuestions() {
        CourseResponse course = createCourse();
        QuestionResponse first = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        QuestionResponse second = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());

        TestResponse created = createTest(course, testBuilder().questions(List.of(item(first, 1), item(second, 2))).build());

        assertAll(
                () -> assertEquals(2, created.getQuestionCount()),
                () -> assertEquals(3, created.getTotalWeight()),
                () -> assertEquals(2, created.getQuestions().size()),
                () -> assertEquals(1, QUESTION_CONTROLLER.getQuestionResponse(first.getId()).as(QuestionResponse.class).getUsedInTests())
        );
    }

    @Test
    @DisplayName("Тест виден в списке юнитов курса как юнит типа TEST")
    void testIsListedAsUnit() {
        CourseResponse course = createCourse();
        TestResponse created = createTest(course, testBuilder().build());

        UnitResponse[] units = UNIT_CONTROLLER.getUnitsResponse(course.getId()).as(UnitResponse[].class);

        assertAll(
                () -> assertEquals(1, units.length),
                () -> assertEquals(created.getUnitId(), units[0].getId()),
                () -> assertEquals("TEST", units[0].getType())
        );
    }

    @ParameterizedTest(name = "repetitions = {0}")
    @ValueSource(strings = {"ALWAYS", "IF_NOT_PASSED", "NEVER"})
    @DisplayName("Каждое допустимое repetitions -> 201")
    void createTestWithEachRepetitions(String repetitions) {
        CourseResponse course = createCourse();

        TestResponse created = createTest(course, testBuilder().repetitions(repetitions).build());

        assertEquals(repetitions, created.getRepetitions());
    }

    @ParameterizedTest(name = "showCorrectAnswers = {0}")
    @ValueSource(strings = {"ALWAYS", "WHEN_PASSED", "NEVER"})
    @DisplayName("Каждое допустимое showCorrectAnswers -> 201")
    void createTestWithEachShowCorrectAnswers(String value) {
        CourseResponse course = createCourse();

        TestResponse created = createTest(course, testBuilder().showCorrectAnswers(value).build());

        assertEquals(value, created.getShowCorrectAnswers());
    }

    @Test
    @DisplayName("Граничные значения: name 80, durationMinutes 1 и 600, passScore 0 и 100, maxAttempts 1 -> 201")
    void createTestWithBoundaryValues() {
        CourseResponse course = createCourse();

        Response min = UNIT_CONTROLLER.createTestResponse(course.getId(),
                testBuilder().name("n".repeat(80)).durationMinutes(1).passScore(0).maxAttempts(1).build());
        Response max = UNIT_CONTROLLER.createTestResponse(course.getId(),
                testBuilder().durationMinutes(600).passScore(100).description("d".repeat(800))
                        .messagePassed("p".repeat(2000)).messageFailed("f".repeat(2000)).build());

        assertAll(
                () -> assertEquals(201, min.statusCode(), min.asString()),
                () -> assertEquals(201, max.statusCode(), max.asString())
        );
    }

    // ---------- POST: negative ----------

    static Stream<Arguments> invalidData() {
        return Stream.of(
                Arguments.of("name = null", testBuilder().name(null).build()),
                Arguments.of("name пустой", testBuilder().name("").build()),
                Arguments.of("name пробелы", testBuilder().name("   ").build()),
                Arguments.of("name 81 символ", testBuilder().name("n".repeat(81)).build()),
                Arguments.of("durationMinutes = 0", testBuilder().durationMinutes(0).build()),
                Arguments.of("durationMinutes = 601", testBuilder().durationMinutes(601).build()),
                Arguments.of("passScore = -1", testBuilder().passScore(-1).build()),
                Arguments.of("passScore = 101", testBuilder().passScore(101).build()),
                Arguments.of("maxAttempts = 0", testBuilder().maxAttempts(0).build()),
                Arguments.of("description 801 символ", testBuilder().description("d".repeat(801)).build()),
                Arguments.of("messagePassed 2001 символ", testBuilder().messagePassed("p".repeat(2001)).build()),
                Arguments.of("messageFailed 2001 символ", testBuilder().messageFailed("f".repeat(2001)).build())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Невалидные данные -> 400")
    void createTestWithInvalidData(String caseName, TestRequest request) {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createTestResponse(course.getId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @ParameterizedTest(name = "Тело: {0}")
    @ValueSource(strings = {"", "{}", "not a json", "[]", "{\"name\":\"x\",\"repetitions\":\"BAD\"}", "{\"name\":\"x\",\"showCorrectAnswers\":\"BAD\"}"})
    @DisplayName("Некорректное тело -> 400")
    void createTestWithMalformedBody(String body) {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createTestRaw(course.getId(), body);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Несуществующий вопрос в списке -> 404")
    void createTestWithUnknownQuestion() {
        CourseResponse course = createCourse();

        Response response = UNIT_CONTROLLER.createTestResponse(course.getId(),
                testBuilder().questions(List.of(new TestRequest.Item(UNKNOWN_ID, 1))).build());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Тест в несуществующем курсе -> 404, с невалидным id -> 400")
    void createTestInWrongCourse() {
        Response unknown = UNIT_CONTROLLER.createTestResponse(UNKNOWN_ID, testBuilder().build());
        Response invalid = UNIT_CONTROLLER.createTestResponse("abc", testBuilder().build());

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- GET /api/units/{id}/test ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /units/{id}/test -> 200, настройки теста")
    void getTest() {
        CourseResponse course = createCourse();
        TestResponse created = createTest(course, testBuilder().passScore(70).durationMinutes(15).build());

        Response response = UNIT_CONTROLLER.getTestResponse(created.getUnitId());
        assertEquals(200, response.statusCode(), response.asString());

        TestResponse actual = response.as(TestResponse.class);
        assertAll(
                () -> assertEquals(created.getUnitId(), actual.getUnitId()),
                () -> assertEquals(created.getName(), actual.getName()),
                () -> assertEquals(70, actual.getPassScore()),
                () -> assertEquals(15, actual.getDurationMinutes())
        );
    }

    @Test
    @DisplayName("GET /test у обычного юнита -> 400 'Unit is not a test'")
    void getTestOfRegularUnit() {
        CourseResponse course = createCourse();
        UnitResponse unit = UNIT_CONTROLLER.createUnit(course.getId(), unitBuilder().build());

        Response response = UNIT_CONTROLLER.getTestResponse(unit.getId());

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("GET /test несуществующего юнита -> 404, невалидного id -> 400")
    void getTestOfWrongUnit() {
        Response unknown = UNIT_CONTROLLER.getTestResponse(UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.getTestResponse("abc");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- PUT /api/units/{id}/test ----------

    @Tag("smoke")
    @Test
    @DisplayName("Обновление -> 200, настройки и список вопросов заменяются")
    void updateTest() {
        CourseResponse course = createCourse();
        QuestionResponse first = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        QuestionResponse second = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse created = createTest(course, testBuilder().questions(List.of(item(first, 1), item(second, 1))).build());
        TestRequest update = testBuilder().passScore(70).durationMinutes(20).questions(List.of(item(second, 5))).build();

        Response response = UNIT_CONTROLLER.updateTestResponse(created.getUnitId(), update);
        assertEquals(200, response.statusCode(), response.asString());

        TestResponse fromGet = UNIT_CONTROLLER.getTestResponse(created.getUnitId()).as(TestResponse.class);
        assertAll(
                () -> assertEquals(update.getName(), response.as(TestResponse.class).getName()),
                () -> assertEquals(update.getName(), fromGet.getName()),
                () -> assertEquals(70, fromGet.getPassScore()),
                () -> assertEquals(20, fromGet.getDurationMinutes()),
                () -> assertEquals(1, fromGet.getQuestionCount()),
                () -> assertEquals(5, fromGet.getTotalWeight())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    @DisplayName("Обновление невалидными данными -> 400")
    void updateTestWithInvalidData(String caseName, TestRequest request) {
        CourseResponse course = createCourse();
        TestResponse created = createTest(course, testBuilder().build());

        Response response = UNIT_CONTROLLER.updateTestResponse(created.getUnitId(), request);

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("Обновление обычного юнита как теста -> 400, несуществующего -> 404, невалидного id -> 400")
    void updateTestNegative() {
        CourseResponse course = createCourse();
        UnitResponse unit = UNIT_CONTROLLER.createUnit(course.getId(), unitBuilder().build());

        Response regular = UNIT_CONTROLLER.updateTestResponse(unit.getId(), testBuilder().build());
        Response unknown = UNIT_CONTROLLER.updateTestResponse(UNKNOWN_ID, testBuilder().build());
        Response invalid = UNIT_CONTROLLER.updateTestRaw("abc", "{\"name\":\"x\"}");

        assertAll(
                () -> assertEquals(400, regular.statusCode(), regular.asString()),
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    @Test
    @DisplayName("Обновление с некорректным телом -> 400")
    void updateTestWithMalformedBody() {
        CourseResponse course = createCourse();
        TestResponse created = createTest(course, testBuilder().build());

        Response response = UNIT_CONTROLLER.updateTestRaw(created.getUnitId(), "not a json");

        assertEquals(400, response.statusCode(), response.asString());
    }

    // ---------- попытки: POST/GET /api/units/{id}/test/attempts ----------

    @Tag("smoke")
    @Test
    @DisplayName("Начало попытки -> 201, вопросы без правильных ответов, дедлайн по durationMinutes")
    void startAttempt() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTest(course, testBuilder().durationMinutes(10).questions(List.of(item(question, 1))).build());
        UNIT_CONTROLLER.startResponse(test.getUnitId());

        Response response = UNIT_CONTROLLER.startAttemptResponse(test.getUnitId());
        assertEquals(201, response.statusCode(), response.asString());

        AttemptResponse attempt = response.as(AttemptResponse.class);
        assertAll(
                () -> assertNotNull(attempt.getId()),
                () -> assertEquals(test.getUnitId(), attempt.getTestId()),
                () -> assertNotNull(attempt.getUserId()),
                () -> assertNotNull(attempt.getStartedAt()),
                () -> assertNull(attempt.getSubmittedAt()),
                () -> assertEquals(10, attempt.getDurationMinutes()),
                () -> assertNotNull(attempt.getDeadline()),
                () -> assertEquals(1, attempt.getQuestions().size()),
                () -> assertFalse(response.asString().contains("\"correct\""), "Ответ раскрывает правильные варианты")
        );
    }

    @Test
    @DisplayName("Пока попытка не сдана, повторный старт возвращает ту же попытку")
    void startAttemptTwiceReturnsSameAttempt() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());

        AttemptResponse first = UNIT_CONTROLLER.startAttempt(test.getUnitId());
        AttemptResponse second = UNIT_CONTROLLER.startAttempt(test.getUnitId());

        assertEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("Начало попытки у обычного юнита -> 400, у несуществующего -> 404, с невалидным id -> 400")
    void startAttemptNegative() {
        CourseResponse course = createCourse();
        UnitResponse unit = UNIT_CONTROLLER.createUnit(course.getId(), unitBuilder().build());

        Response regular = UNIT_CONTROLLER.startAttemptResponse(unit.getId());
        Response unknown = UNIT_CONTROLLER.startAttemptResponse(UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.startAttemptResponse("abc");

        assertAll(
                () -> assertEquals(400, regular.statusCode(), regular.asString()),
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    @Test
    @DisplayName("GET /attempts: попытка в списке, фильтр userId отсекает чужие")
    void listAttempts() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse attempt = UNIT_CONTROLLER.startAttempt(test.getUnitId());

        Response all = UNIT_CONTROLLER.attemptsResponse(test.getUnitId());
        Response mine = UNIT_CONTROLLER.attemptsResponse(test.getUnitId(), attempt.getUserId());
        Response foreign = UNIT_CONTROLLER.attemptsResponse(test.getUnitId(), UNKNOWN_ID);

        assertAll(
                () -> assertEquals(200, all.statusCode(), all.asString()),
                () -> assertEquals(1, all.jsonPath().getList("$").size()),
                () -> assertEquals(attempt.getId().toString(), all.jsonPath().getString("[0].id")),
                () -> assertEquals(1, mine.jsonPath().getList("$").size()),
                () -> assertEquals(0, foreign.jsonPath().getList("$").size())
        );
    }

    @Test
    @DisplayName("Список попыток несуществующего юнита -> 404, невалидного id -> 400")
    void listAttemptsOfWrongUnit() {
        Response unknown = UNIT_CONTROLLER.attemptsResponse(UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.attemptsResponse("abc");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    @Test
    @DisplayName("GET /attempts/{attemptId} -> 200, та же попытка")
    void getAttempt() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse attempt = UNIT_CONTROLLER.startAttempt(test.getUnitId());

        Response response = UNIT_CONTROLLER.attemptResponse(test.getUnitId(), attempt.getId());
        assertEquals(200, response.statusCode(), response.asString());

        AttemptResponse actual = response.as(AttemptResponse.class);
        assertAll(
                () -> assertEquals(attempt.getId(), actual.getId()),
                () -> assertEquals(test.getUnitId(), actual.getTestId()),
                () -> assertNull(actual.getSubmittedAt())
        );
    }

    @Test
    @DisplayName("Несуществующая попытка -> 404, невалидный id попытки -> 400")
    void getWrongAttempt() {
        CourseResponse course = createCourse();
        TestResponse test = createTest(course, testBuilder().build());

        Response unknown = UNIT_CONTROLLER.attemptResponse(test.getUnitId(), UNKNOWN_ID);
        Response invalid = UNIT_CONTROLLER.attemptResponse(test.getUnitId(), "abc");

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }

    // ---------- POST /api/units/{id}/test/attempts/{attemptId}/submit ----------

    @Tag("smoke")
    @Test
    @DisplayName("Верный ответ -> passed = true, score = 100")
    void submitCorrectAnswer() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse attempt = UNIT_CONTROLLER.startAttempt(test.getUnitId());

        // правильный вариант в questionBuilder() — первый, его id = 0
        Response response = UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), attempt.getId(),
                Map.of("answers", Map.of(question.getId().toString(), List.of(0))));
        assertEquals(200, response.statusCode(), response.asString());

        AttemptResponse result = response.as(AttemptResponse.class);
        assertAll(
                () -> assertNotNull(result.getSubmittedAt()),
                () -> assertTrue(result.getPassed()),
                () -> assertEquals(100, result.getScore()),
                () -> assertEquals(50, result.getPassScore())
        );
    }

    @Test
    @DisplayName("Неверный ответ -> passed = false, score = 0")
    void submitWrongAnswer() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse attempt = UNIT_CONTROLLER.startAttempt(test.getUnitId());

        Response response = UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), attempt.getId(),
                Map.of("answers", Map.of(question.getId().toString(), List.of(1))));

        AttemptResponse result = response.as(AttemptResponse.class);
        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertFalse(result.getPassed()),
                () -> assertEquals(0, result.getScore())
        );
    }

    @Test
    @DisplayName("Пустой ответ {} -> 200, passed = false")
    void submitEmptyAnswers() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse attempt = UNIT_CONTROLLER.startAttempt(test.getUnitId());

        Response response = UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), attempt.getId(), Map.of());

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertFalse(response.as(AttemptResponse.class).getPassed())
        );
    }

    @Test
    @DisplayName("После отправки попытка в GET имеет submittedAt и score")
    void submittedAttemptHasScore() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse attempt = UNIT_CONTROLLER.startAttempt(test.getUnitId());
        UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), attempt.getId(), Map.of());

        AttemptResponse fromGet = UNIT_CONTROLLER.attemptResponse(test.getUnitId(), attempt.getId()).as(AttemptResponse.class);

        assertAll(
                () -> assertNotNull(fromGet.getSubmittedAt()),
                () -> assertNotNull(fromGet.getScore())
        );
    }

    @Test
    @DisplayName("Повторная отправка той же попытки -> 409")
    void submitTwice() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse attempt = UNIT_CONTROLLER.startAttempt(test.getUnitId());
        UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), attempt.getId(), Map.of());

        Response response = UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), attempt.getId(), Map.of());

        assertEquals(409, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("После сдачи можно начать новую попытку (repetitions = ALWAYS)")
    void newAttemptAfterSubmit() {
        CourseResponse course = createCourse();
        QuestionResponse question = QUESTION_CONTROLLER.createQuestion(course.getId(), questionBuilder().build());
        TestResponse test = createTestWithQuestion(course, question);
        UNIT_CONTROLLER.startResponse(test.getUnitId());
        AttemptResponse first = UNIT_CONTROLLER.startAttempt(test.getUnitId());
        UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), first.getId(), Map.of());

        AttemptResponse second = UNIT_CONTROLLER.startAttempt(test.getUnitId());

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("Отправка несуществующей попытки -> 404, с невалидным id -> 400")
    void submitWrongAttempt() {
        CourseResponse course = createCourse();
        TestResponse test = createTest(course, testBuilder().build());

        Response unknown = UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), UNKNOWN_ID, Map.of());
        Response invalid = UNIT_CONTROLLER.submitAttemptResponse(test.getUnitId(), "abc", Map.of());

        assertAll(
                () -> assertEquals(404, unknown.statusCode(), unknown.asString()),
                () -> assertEquals(400, invalid.statusCode(), invalid.asString())
        );
    }
}
