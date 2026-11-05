package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.JsonUtils;

import java.util.Arrays;
import java.util.List;

import static kg.jazzlms.constants.Endpoints.USERS;

public class UserController extends ApiRequest {

    public UserController(String url) {
        super(url);
    }

    public UserResponse createUser(UserRequest request) {
        return post(USERS, JsonUtils.toJson(request)).as(UserResponse.class);
    }

    public List<UserResponse> getAllUsers() {
        return Arrays.asList(super.get(USERS).as(UserResponse[].class));
    }

    public UserResponse getUserById(String id) {
        return get(USERS + "/" + id).as(UserResponse.class);
    }

    public Response createUserResponse(UserRequest request) {
        return post(USERS, JsonUtils.toJson(request));
    }

    // для битого JSON, неверных типов и пустого тела
    public Response createUserRaw(String body) {
        return post(USERS, body);
    }

}