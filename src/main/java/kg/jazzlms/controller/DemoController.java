package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;

import static io.restassured.RestAssured.given;
import static kg.jazzlms.constants.Endpoints.DEMO_STATUS;

/** GET /api/demo/status/{code} — сервис возвращает заданный статус (200, 201, 204, 300, 301, 403, 500) */
public class DemoController extends ApiRequest {

    public DemoController(String url) {
        super(url);
    }

    public DemoController(String url, String token) {
        super(url, token);
    }

    /** Редиректы (301) не следуем: нужен сам ответ сервиса */
    public Response statusResponse(Object code) {
        this.response = given().spec(requestSpecification)
                .redirects().follow(false)
                .get(String.format(DEMO_STATUS, code));
        return this.response;
    }
}
