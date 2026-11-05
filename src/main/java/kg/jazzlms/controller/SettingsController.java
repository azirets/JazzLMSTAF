package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.SettingsRequest;
import kg.jazzlms.utils.JsonUtils;

import static kg.jazzlms.constants.Endpoints.SETTINGS;

/** /api/settings (user service) */
public class SettingsController extends ApiRequest {

    public SettingsController(String url) {
        super(url);
    }

    public SettingsController(String url, String token) {
        super(url, token);
    }

    public Response getSettingsResponse() {
        return get(SETTINGS);
    }

    /** Текущие настройки как объект (нужны, чтобы вернуть всё как было) */
    public SettingsRequest getSettings() {
        return getSettingsResponse().then().statusCode(200).extract().as(SettingsRequest.class);
    }

    public Response updateSettingsResponse(SettingsRequest request) {
        return put(SETTINGS, JsonUtils.toJson(request));
    }

    public Response updateSettingsRaw(String body) {
        return put(SETTINGS, body);
    }
}
