package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.BranchRequest;
import kg.jazzlms.models.response.BranchResponse;
import kg.jazzlms.utils.JsonUtils;

import static kg.jazzlms.constants.Endpoints.*;

/** /api/branches/** (user service) */
public class BranchController extends ApiRequest {

    public BranchController(String url) {
        super(url);
    }

    public BranchController(String url, String token) {
        super(url, token);
    }

    public BranchResponse createBranch(BranchRequest request) {
        return createBranchResponse(request).then().statusCode(201).extract().as(BranchResponse.class);
    }

    public Response createBranchResponse(BranchRequest request) {
        return post(BRANCHES, JsonUtils.toJson(request));
    }

    /** Для битого JSON и пустого тела */
    public Response createBranchRaw(String body) {
        return post(BRANCHES, body);
    }

    public Response getBranchesResponse() {
        return get(BRANCHES);
    }

    public Response getBranchResponse(Object id) {
        return get(String.format(BRANCHES_ID, id));
    }

    public Response updateBranchResponse(Object id, BranchRequest request) {
        return put(String.format(BRANCHES_ID, id), JsonUtils.toJson(request));
    }

    public Response updateBranchRaw(Object id, String body) {
        return put(String.format(BRANCHES_ID, id), body);
    }

    public Response deleteBranchResponse(Object id) {
        return delete(String.format(BRANCHES_ID, id));
    }

    public Response branchUsersResponse(Object branchId) {
        return get(String.format(BRANCHES_USERS, branchId));
    }

    public Response addUserResponse(Object branchId, Object userId) {
        return post(String.format(BRANCHES_USER, branchId, userId), "");
    }

    public Response removeUserResponse(Object branchId, Object userId) {
        return delete(String.format(BRANCHES_USER, branchId, userId));
    }
}
