package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.GroupRequest;
import kg.jazzlms.models.response.GroupResponse;
import kg.jazzlms.utils.JsonUtils;

import static kg.jazzlms.constants.Endpoints.*;

/** /api/groups/** (user service) */
public class GroupController extends ApiRequest {

    public GroupController(String url) {
        super(url);
    }

    public GroupController(String url, String token) {
        super(url, token);
    }

    public GroupResponse createGroup(GroupRequest request) {
        return createGroupResponse(request).then().statusCode(201).extract().as(GroupResponse.class);
    }

    public Response createGroupResponse(GroupRequest request) {
        return post(GROUPS, JsonUtils.toJson(request));
    }

    /** Для битого JSON и пустого тела */
    public Response createGroupRaw(String body) {
        return post(GROUPS, body);
    }

    public Response getGroupsResponse() {
        return get(GROUPS);
    }

    public Response getGroupResponse(Object id) {
        return get(String.format(GROUPS_ID, id));
    }

    public Response updateGroupResponse(Object id, GroupRequest request) {
        return put(String.format(GROUPS_ID, id), JsonUtils.toJson(request));
    }

    public Response updateGroupRaw(Object id, String body) {
        return put(String.format(GROUPS_ID, id), body);
    }

    public Response deleteGroupResponse(Object id) {
        return delete(String.format(GROUPS_ID, id));
    }

    public Response groupUsersResponse(Object groupId) {
        return get(String.format(GROUPS_USERS, groupId));
    }

    public Response addUserResponse(Object groupId, Object userId) {
        return post(String.format(GROUPS_USER, groupId, userId), "");
    }

    public Response removeUserResponse(Object groupId, Object userId) {
        return delete(String.format(GROUPS_USER, groupId, userId));
    }
}
