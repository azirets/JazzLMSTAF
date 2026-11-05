package kg.jazzlms.utils;

import com.github.javafaker.Faker;
import kg.jazzlms.models.request.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EntityManager {

    private static final Faker faker = new Faker();

    /** Билдер с валидной базой: в тесте переопределяешь только нужные поля */
    public static UserRequest.UserRequestBuilder userBuilder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return UserRequest.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .email(suffix + "." + faker.internet().emailAddress())
                .username(faker.name().username() + "_" + suffix);
    }

    /** Минимальный пользователь (как раньше) */
    public static UserRequest generateUser() {
        return userBuilder().build();
    }

    /** Пользователь, который может войти: active = true и известный пароль */
    public static final String DEFAULT_PASSWORD = "Pass123!";

    public static UserRequest.UserRequestBuilder loginableUserBuilder() {
        return userBuilder().password(DEFAULT_PASSWORD).active(true);
    }

    /** Группа только с обязательным полем name */
    public static GroupRequest.GroupRequestBuilder groupBuilder() {
        return GroupRequest.builder().name("Group " + UUID.randomUUID().toString().substring(0, 8));
    }

    /** Филиал только с обязательным полем name (a-z, 0-9, дефис) */
    public static BranchRequest.BranchRequestBuilder branchBuilder() {
        return BranchRequest.builder().name("br-" + UUID.randomUUID().toString().substring(0, 8));
    }

    // ---------- course service: категории, шаблоны сертификатов, юниты, тесты, вопросы ----------

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static CategoryRequest.CategoryRequestBuilder categoryBuilder() {
        return CategoryRequest.builder().name("Category " + shortId());
    }

    public static CertificateTemplateRequest.CertificateTemplateRequestBuilder templateBuilder() {
        return CertificateTemplateRequest.builder()
                .name("Template " + shortId())
                .background("classic")
                .title("Certificate")
                .body("This is to certify that {user} completed {course} on {date}.");
    }

    /** Юнит-контент: обязательные name и type = CONTENT */
    public static UnitRequest.UnitRequestBuilder unitBuilder() {
        return UnitRequest.builder().name("Unit " + shortId()).type("CONTENT").textContent("Text of the unit");
    }

    public static TestRequest.TestRequestBuilder testBuilder() {
        return TestRequest.builder().name("Test " + shortId());
    }

    /** Вопрос с одним правильным ответом из двух (MULTIPLE_CHOICE) */
    public static QuestionRequest.QuestionRequestBuilder questionBuilder() {
        return QuestionRequest.builder()
                .type("MULTIPLE_CHOICE")
                .text("Question " + shortId() + "?")
                .data(Map.of("answers", List.of(
                        Map.of("text", "Right", "correct", true),
                        Map.of("text", "Wrong", "correct", false))));
    }

    /** Правило уведомления с обязательными полями: на создание пользователя, получатель — сам пользователь */
    public static NotificationRuleRequest.NotificationRuleRequestBuilder ruleBuilder() {
        return NotificationRuleRequest.builder()
                .name("Rule " + shortId())
                .eventType("USER_CREATED")
                .recipient("RELATED_USER")
                .subjectTemplate("Subject {{firstName}}")
                .bodyTemplate("Body {{firstName}} {{lastName}}");
    }

    /** Курс только с обязательным полем name (+ уникальный code) */
    public static CourseRequest.CourseRequestBuilder courseBuilder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return CourseRequest.builder()
                .name(faker.educator().course() + " " + suffix)
                .code("C-" + suffix);
    }

    public static CourseRequest generateCourse() {
        return courseBuilder().build();
    }
}