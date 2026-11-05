import io.restassured.response.Response;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.controller.CertificateController;
import kg.jazzlms.controller.CourseController;
import kg.jazzlms.controller.UserController;
import kg.jazzlms.models.request.CourseRequest;
import kg.jazzlms.models.response.CertificateResponse;
import kg.jazzlms.models.response.CertificateTemplateResponse;
import kg.jazzlms.models.response.CourseResponse;
import kg.jazzlms.models.response.EnrollmentResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static kg.jazzlms.utils.EntityManager.templateBuilder;
import static org.junit.jupiter.api.Assertions.*;

/** Выданные сертификаты: появляются, когда прогресс записи доходит до 100 */
@Tag("regression")
@DisplayName("Course service: выданные сертификаты (/api/certificates)")
public class IssuedCertificateTest {

    private static final CourseController COURSE_CONTROLLER = new CourseController(Endpoints.COURSE_URL);
    private static final CertificateController CERT_CONTROLLER = new CertificateController(Endpoints.COURSE_URL);
    private static final UserController USER_CONTROLLER = new UserController(Endpoints.BASE_URL);
    private static final List<UUID> COURSES = new ArrayList<>();
    private static final List<UUID> USERS = new ArrayList<>();
    private static final List<UUID> TEMPLATES = new ArrayList<>();
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private CourseResponse createCourse(CourseRequest request) {
        CourseResponse created = COURSE_CONTROLLER.createCourse(request);
        COURSES.add(created.getId());
        return created;
    }

    private UserResponse createUser() {
        UserResponse created = USER_CONTROLLER.createUser(EntityManager.userBuilder().active(true).build());
        USERS.add(created.getId());
        return created;
    }

    /** Записывает пользователя на курс и доводит прогресс до 100 */
    private void completeCourse(CourseResponse course, UserResponse user) {
        EnrollmentResponse enrollment = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "LEARNER")
                .then().statusCode(201).extract().as(EnrollmentResponse.class);
        COURSE_CONTROLLER.updateProgressResponse(enrollment.getId(), 100);
    }

    private CertificateResponse[] certificatesOf(UserResponse user) {
        return CERT_CONTROLLER.userCertificatesResponse(user.getId()).as(CertificateResponse[].class);
    }

    @AfterAll
    static void cleanUp() {
        COURSES.forEach(id -> {
            COURSE_CONTROLLER.deleteCourseResponse(id);
            COURSE_CONTROLLER.deleteCoursePermanentlyResponse(id);
        });
        USERS.forEach(USER_CONTROLLER::deleteUserResponse);
        TEMPLATES.forEach(CERT_CONTROLLER::deleteTemplateResponse);
    }

    // ---------- выдача ----------

    @Tag("smoke")
    @Test
    @DisplayName("Прогресс 100 -> сертификат выдан: код, дата, курс, шаблон по умолчанию")
    void certificateIsIssuedOnCompletion() {
        CourseResponse course = createCourse(EntityManager.generateCourse());
        UserResponse user = createUser();

        completeCourse(course, user);

        CertificateResponse[] certificates = certificatesOf(user);
        assertEquals(1, certificates.length, "Ожидали один сертификат");
        CertificateResponse certificate = certificates[0];
        assertAll(
                () -> assertNotNull(certificate.getId()),
                () -> assertNotNull(certificate.getCode()),
                () -> assertTrue(certificate.getCode().startsWith("JZ-")),
                () -> assertNotNull(certificate.getIssuedAt()),
                () -> assertEquals(course.getId(), certificate.getCourseId()),
                () -> assertEquals(course.getName(), certificate.getCourseName()),
                () -> assertNotNull(certificate.getTemplateId())
        );
    }

    @Test
    @DisplayName("Прогресс 50 -> сертификата нет")
    void noCertificateBeforeCompletion() {
        CourseResponse course = createCourse(EntityManager.generateCourse());
        UserResponse user = createUser();
        EnrollmentResponse enrollment = COURSE_CONTROLLER.enrollResponse(course.getId(), user.getId(), "LEARNER")
                .as(EnrollmentResponse.class);

        COURSE_CONTROLLER.updateProgressResponse(enrollment.getId(), 50);

        assertEquals(0, certificatesOf(user).length);
    }

    @Test
    @DisplayName("Курс с noCertificate = true -> сертификат не выдаётся")
    void noCertificateForCourseWithoutCertificate() {
        CourseResponse course = createCourse(EntityManager.courseBuilder().noCertificate(true).build());
        UserResponse user = createUser();

        completeCourse(course, user);

        assertEquals(0, certificatesOf(user).length);
    }

    @Test
    @DisplayName("Курс с выбранным шаблоном -> в сертификате его templateId")
    void certificateUsesCourseTemplate() {
        CertificateTemplateResponse template = CERT_CONTROLLER.createTemplate(templateBuilder().build());
        TEMPLATES.add(template.getId());
        CourseResponse course = createCourse(EntityManager.courseBuilder().certificateTemplateId(template.getId()).build());
        UserResponse user = createUser();

        completeCourse(course, user);

        CertificateResponse[] certificates = certificatesOf(user);
        assertAll(
                () -> assertEquals(1, certificates.length),
                () -> assertEquals(template.getId(), certificates[0].getTemplateId())
        );
    }

    // ---------- GET /api/certificates/{id} и /view ----------

    @Tag("smoke")
    @Test
    @DisplayName("GET /certificates/{id} -> 200, данные совпадают с выданным")
    void getCertificateById() {
        CourseResponse course = createCourse(EntityManager.generateCourse());
        UserResponse user = createUser();
        completeCourse(course, user);
        CertificateResponse issued = certificatesOf(user)[0];

        Response response = CERT_CONTROLLER.getCertificateResponse(issued.getId());
        assertEquals(200, response.statusCode(), response.asString());

        CertificateResponse actual = response.as(CertificateResponse.class);
        assertAll(
                () -> assertEquals(issued.getId(), actual.getId()),
                () -> assertEquals(issued.getCode(), actual.getCode()),
                () -> assertEquals(course.getId(), actual.getCourseId())
        );
    }

    @Test
    @DisplayName("GET /certificates/{id}/view -> 200, HTML с именем ученика, курсом и кодом")
    void viewCertificate() {
        CourseResponse course = createCourse(EntityManager.generateCourse());
        UserResponse user = createUser();
        completeCourse(course, user);
        CertificateResponse issued = certificatesOf(user)[0];

        Response response = CERT_CONTROLLER.viewCertificateResponse(issued.getId());
        assertEquals(200, response.statusCode());

        String html = response.asString();
        assertAll(
                () -> assertTrue(html.contains(user.getFirstName() + " " + user.getLastName()), "Нет имени ученика"),
                () -> assertTrue(html.contains(course.getName()), "Нет названия курса"),
                () -> assertTrue(html.contains(issued.getCode()), "Нет кода сертификата")
        );
    }

    @Test
    @DisplayName("Несуществующий сертификат -> 404")
    void getUnknownCertificate() {
        Response get = CERT_CONTROLLER.getCertificateResponse(UNKNOWN_ID);
        Response view = CERT_CONTROLLER.viewCertificateResponse(UNKNOWN_ID);

        assertAll(
                () -> assertEquals(404, get.statusCode(), get.asString()),
                () -> assertEquals(404, view.statusCode(), view.asString())
        );
    }

    @Test
    @DisplayName("Сертификат с невалидным id -> 400")
    void getCertificateWithInvalidId() {
        Response get = CERT_CONTROLLER.getCertificateResponse("abc");
        Response view = CERT_CONTROLLER.viewCertificateResponse("abc");

        assertAll(
                () -> assertEquals(400, get.statusCode(), get.asString()),
                () -> assertEquals(400, view.statusCode(), view.asString())
        );
    }

    // ---------- GET /api/certificates/user/{userId} и /my ----------

    @Test
    @DisplayName("Сертификаты пользователя без выданных -> 200 и пустой список")
    void userWithoutCertificates() {
        Response response = CERT_CONTROLLER.userCertificatesResponse(UNKNOWN_ID);

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertEquals(0, response.jsonPath().getList("$").size())
        );
    }

    @Test
    @DisplayName("Сертификаты пользователя с невалидным id -> 400")
    void userCertificatesWithInvalidId() {
        Response response = CERT_CONTROLLER.userCertificatesResponse("abc");

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("GET /certificates/my -> 200, список сертификатов текущего пользователя")
    void myCertificates() {
        Response response = CERT_CONTROLLER.myCertificatesResponse();

        assertAll(
                () -> assertEquals(200, response.statusCode(), response.asString()),
                () -> assertTrue(Arrays.stream(response.as(CertificateResponse[].class)).allMatch(c -> c.getCode() != null))
        );
    }
}
