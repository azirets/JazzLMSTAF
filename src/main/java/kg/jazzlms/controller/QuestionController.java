package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.QuestionRequest;
import kg.jazzlms.models.response.QuestionResponse;
import kg.jazzlms.utils.JsonUtils;

import java.util.HashMap;
import java.util.Map;

import static kg.jazzlms.constants.Endpoints.*;

/** Банк вопросов: /api/courses/{id}/questions, /api/questions/{id} */
public class QuestionController extends ApiRequest {

    public QuestionController(String url) {
        super(url);
    }

    public QuestionController(String url, String token) {
        super(url, token);
    }

    public QuestionResponse createQuestion(Object courseId, QuestionRequest request) {
        return createQuestionResponse(courseId, request).then().statusCode(201).extract().as(QuestionResponse.class);
    }

    public Response createQuestionResponse(Object courseId, QuestionRequest request) {
        return post(String.format(COURSES_QUESTIONS, courseId), JsonUtils.toJson(request));
    }

    public Response createQuestionRaw(Object courseId, String body) {
        return post(String.format(COURSES_QUESTIONS, courseId), body);
    }

    /** GET /api/courses/{id}/questions, необязательные all и search */
    public Response getQuestionsResponse(Object courseId) {
        return get(String.format(COURSES_QUESTIONS, courseId));
    }

    public Response getQuestionsResponse(Object courseId, Boolean all, String search) {
        Map<String, Object> params = new HashMap<>();
        if (all != null) {
            params.put("all", all);
        }
        if (search != null) {
            params.put("search", search);
        }
        return get(String.format(COURSES_QUESTIONS, courseId), params);
    }

    public Response getQuestionResponse(Object id) {
        return get(String.format(QUESTIONS_ID, id));
    }

    public Response updateQuestionResponse(Object id, QuestionRequest request) {
        return put(String.format(QUESTIONS_ID, id), JsonUtils.toJson(request));
    }

    public Response updateQuestionRaw(Object id, String body) {
        return put(String.format(QUESTIONS_ID, id), body);
    }

    public Response deleteQuestionResponse(Object id) {
        return delete(String.format(QUESTIONS_ID, id));
    }

    /** POST /api/courses/{id}/questions/import?dryRun=; тело {"data": "<текст с ANSWER:>"} */
    public Response importResponse(Object courseId, String body, Object dryRun) {
        String url = String.format(COURSES_QUESTIONS_IMPORT, courseId);
        if (dryRun != null) {
            url += "?dryRun=" + dryRun;
        }
        return post(url, body);
    }
}
