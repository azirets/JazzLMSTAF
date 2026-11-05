package kg.jazzlms.controller;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import kg.jazzlms.models.request.ChangePasswordRequest;
import kg.jazzlms.models.request.LoginRequest;
import kg.jazzlms.models.request.RegisterRequest;
import kg.jazzlms.models.response.LoginResponse;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.JsonUtils;

import static io.restassured.RestAssured.given;
import static kg.jazzlms.constants.Endpoints.*;

/** /api/auth/** — логин публичный, поэтому без токена в конструкторе */
public class AuthController {

    private final String url;

    public AuthController(String url) {
        this.url = url;
    }

    public Response loginResponse(String username, String password) {
        return given()
                .baseUri(url)
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .filter(new AllureRestAssured())
                .body(new LoginRequest(username, password))
                .post(LOGIN);
    }

    public LoginResponse login(String username, String password) {
        return loginResponse(username, password).then().statusCode(200).extract().as(LoginResponse.class);
    }

    /** Логин с сырым телом: пустое, битый JSON и т.п. */
    public Response loginRaw(String body) {
        return given()
                .baseUri(url)
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .filter(new AllureRestAssured())
                .body(body)
                .post(LOGIN);
    }

    /** POST /api/auth/register — публичный, токен не нужен */
    public Response registerResponse(RegisterRequest request) {
        return given()
                .baseUri(url)
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .filter(new AllureRestAssured())
                .body(JsonUtils.toJson(request))
                .post(AUTH_REGISTER);
    }

    /** GET /api/auth/settings — публичные настройки */
    public Response publicSettingsResponse() {
        return given()
                .baseUri(url)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .filter(new AllureRestAssured())
                .get(AUTH_SETTINGS);
    }

    public Response meResponse(String token) {
        return given()
                .baseUri(url)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .filter(new AllureRestAssured())
                .header("Authorization", "Bearer " + token)
                .get(ME);
    }

    /** POST /api/auth/password от имени пользователя с этим токеном */
    public Response changePasswordResponse(String token, ChangePasswordRequest request) {
        return given()
                .baseUri(url)
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .filter(new AllureRestAssured())
                .header("Authorization", "Bearer " + token)
                .body(JsonUtils.toJson(request))
                .post(AUTH_PASSWORD);
    }

    /** POST /api/auth/impersonate/{userId} — вход от имени другого пользователя (админ) */
    public Response impersonateResponse(String token, Object userId) {
        return given()
                .baseUri(url)
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .filter(new AllureRestAssured())
                .header("Authorization", "Bearer " + token)
                .post(String.format(AUTH_IMPERSONATE, userId));
    }

    public UserResponse me(String token) {
        return given()
                .baseUri(url)
                .header("Authorization", "Bearer " + token)
                .get(ME)
                .then().statusCode(200).extract().as(UserResponse.class);
    }
}
