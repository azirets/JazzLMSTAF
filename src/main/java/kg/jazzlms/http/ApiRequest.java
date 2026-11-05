package kg.jazzlms.http;

import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import kg.jazzlms.config.ConfigReader;
import kg.jazzlms.constants.Endpoints;
import kg.jazzlms.models.request.LoginRequest;
import kg.jazzlms.models.response.LoginResponse;

import static io.restassured.RestAssured.given;

public class ApiRequest {
    protected String url;
    protected RequestSpecification requestSpecification;
    protected Response response;
    private static final String SLASH = "/";
    private static String token; // кэш токена на весь прогон тестов

    public ApiRequest(String url) {
        this.url = url;
        this.requestSpecification = given()
                .baseUri(url)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getToken(url))
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
    }

    private static synchronized String getToken(String url) {
        if (token == null) {
            LoginRequest loginRequest = new LoginRequest(
                    ConfigReader.getValue("admin.username"),
                    ConfigReader.getValue("admin.password"));

            token = given()
                    .baseUri(url)
                    .contentType(ContentType.JSON)
                    .body(loginRequest)
                    .post(Endpoints.LOGIN)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(LoginResponse.class)
                    .getToken();
        }
        return token;
    }

    protected static String getEndpoint(String... endpoints) {
        StringBuilder endpoint = new StringBuilder();
        for (String arg : endpoints) {
            endpoint.append(arg).append(SLASH);
        }
        return endpoint.substring(0, endpoint.length() - 1);
    }

    public Response get(String endpoint) {
        this.response = given().spec(requestSpecification).get(endpoint);
        return this.response;
    }

    public Response post(String endpoint, String body) {
        this.response = given().spec(requestSpecification).body(body).post(endpoint);
        return this.response;
    }

    public Response put(String endpoint, String body) {
        this.response = given().spec(requestSpecification).body(body).put(endpoint);
        return this.response;
    }

    public Response patch(String endpoint, String body) {
        this.response = given().spec(requestSpecification).body(body).patch(endpoint);
        return this.response;
    }

    public Response delete(String endpoint) {
        this.response = given().spec(requestSpecification).delete(endpoint);
        return this.response;
    }
}