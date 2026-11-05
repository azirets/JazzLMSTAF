package kg.jazzlms.assertions;

import io.restassured.internal.common.assertion.Assertion;
import io.restassured.response.Response;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.LoginResponse;
import kg.jazzlms.models.response.UserResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UserAssertions {

    public static void assertStatusCode(Response response, int expected) {
        assertEquals(expected, response.statusCode(),
                "Неверный статус код. Тело ответа: " + response.asString());
    }

    public static void assertUserCreated(UserRequest expected, UserResponse actual) {
        assertNotNull(actual, "Ответ пустой");
        assertNotNull(actual.getId(), "id не должен быть null");
        assertEquals(expected.getFirstName(), actual.getFirstName(), "firstName");
        assertEquals(expected.getLastName(), actual.getLastName(), "lastName");
        assertEquals(expected.getEmail(), actual.getEmail(), "email");
        assertEquals(expected.getUsername(), actual.getUsername(), "username");
        assertEquals(expected.getUserType(), actual.getUserType(), "userType");
        assertEquals(expected.getTimeZone(), actual.getTimeZone(), "timeZone");
        assertEquals(expected.getLanguage(), actual.getLanguage(), "language");
        assertEquals(expected.isActive(), actual.isActive(), "active");
        assertEquals(expected.isExcludeFromEmails(), actual.isExcludeFromEmails(), "excludeFromEmails");
        assertNotNull(actual.getCreatedAt(), "createdAt не должен быть null");
    }

    public static void assertUsersListNotEmpty(List<LoginResponse.User> users) {
        assertNotNull(users, "Список пользователей null");
        assertFalse(users.isEmpty(), "Список пользователей пустой");
    }

//    public static void assertUserInList(@org.jetbrains.annotations.UnknownNullability List<LoginResponse.User> users, String username) {
//        boolean found = users.stream()
//                .anyMatch(u -> username.equals(u.getUsername()));
//        assertTrue(found, "Пользователь " + username + " не найден в списке");
//    }
}