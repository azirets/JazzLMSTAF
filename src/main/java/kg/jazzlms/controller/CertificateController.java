package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.CertificateTemplateRequest;
import kg.jazzlms.models.response.CertificateTemplateResponse;
import kg.jazzlms.utils.JsonUtils;

import static kg.jazzlms.constants.Endpoints.*;

/** /api/certificates/** (course service): шаблоны и выданные сертификаты */
public class CertificateController extends ApiRequest {

    public CertificateController(String url) {
        super(url);
    }

    public CertificateController(String url, String token) {
        super(url, token);
    }

    // ---------- шаблоны ----------

    public CertificateTemplateResponse createTemplate(CertificateTemplateRequest request) {
        return createTemplateResponse(request).then().statusCode(201).extract().as(CertificateTemplateResponse.class);
    }

    public Response createTemplateResponse(CertificateTemplateRequest request) {
        return post(CERT_TEMPLATES, JsonUtils.toJson(request));
    }

    public Response createTemplateRaw(String body) {
        return post(CERT_TEMPLATES, body);
    }

    public Response getTemplatesResponse() {
        return get(CERT_TEMPLATES);
    }

    public Response getBackgroundsResponse() {
        return get(CERT_BACKGROUNDS);
    }

    public Response updateTemplateResponse(Object id, CertificateTemplateRequest request) {
        return put(String.format(CERT_TEMPLATES_ID, id), JsonUtils.toJson(request));
    }

    public Response updateTemplateRaw(Object id, String body) {
        return put(String.format(CERT_TEMPLATES_ID, id), body);
    }

    public Response deleteTemplateResponse(Object id) {
        return delete(String.format(CERT_TEMPLATES_ID, id));
    }

    public Response resetTemplateResponse(Object id) {
        return post(String.format(CERT_TEMPLATES_RESET, id), "");
    }

    public Response previewTemplateResponse(Object id) {
        return get(String.format(CERT_TEMPLATES_PREVIEW, id));
    }

    // ---------- выданные сертификаты ----------

    public Response getCertificateResponse(Object id) {
        return get(String.format(CERT_ID, id));
    }

    public Response viewCertificateResponse(Object id) {
        return get(String.format(CERT_VIEW, id));
    }

    public Response userCertificatesResponse(Object userId) {
        return get(String.format(CERT_USER, userId));
    }

    public Response myCertificatesResponse() {
        return get(CERT_MY);
    }
}
