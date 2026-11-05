package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.UserRequest;
import kg.jazzlms.models.response.UserResponse;
import kg.jazzlms.utils.JsonUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static kg.jazzlms.constants.Endpoints.*;

public class UserController extends ApiRequest {

    public UserController(String url) {
        super(url);
    }

    public UserController(String url, String token) {
        super(url, token);
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

    /** PUT /api/users/{id}: все поля необязательные, отправляем только нужные */
    public Response updateUserResponse(UUID id, Map<String, Object> fields) {
        return put(String.format(USERS_ID, id), JsonUtils.toJson(fields));
    }

    public Response deleteUserResponse(UUID id) {
        return delete(String.format(USERS_ID, id));
    }

    public Response getUserByIdResponse(Object id) {
        return get(String.format(USERS_ID, id));
    }

    /** GET /api/users?search=&active= — передаём только те параметры, что нужны */
    public Response getUsersResponse(Map<String, ?> queryParams) {
        return get(USERS, queryParams);
    }

    public Response getUsersResponse() {
        return get(USERS);
    }

    /** PUT с произвольным id (например "abc") и сырым телом */
    public Response updateUserRaw(Object id, String body) {
        return put(String.format(USERS_ID, id), body);
    }

    public Response deleteUserResponse(Object id) {
        return delete(String.format(USERS_ID, id));
    }

    public Response statsResponse() {
        return get(USERS_STATS);
    }

    public Response exportResponse() {
        return get(USERS_EXPORT);
    }

    public Response exportResponse(Map<String, ?> queryParams) {
        return get(USERS_EXPORT, queryParams);
    }

    /** POST /api/users/import — multipart, файл в поле "file" */
    public Response importResponse(java.io.File file) {
        return postFile(USERS_IMPORT, file);
    }

    public Response userGroupsResponse(Object userId) {
        return get(String.format(USERS_GROUPS, userId));
    }

    public Response userBranchesResponse(Object userId) {
        return get(String.format(USERS_BRANCHES, userId));
    }
}
