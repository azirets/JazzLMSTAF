package kg.jazzlms.controller;

import io.restassured.response.Response;
import kg.jazzlms.http.ApiRequest;
import kg.jazzlms.models.request.CategoryRequest;
import kg.jazzlms.models.response.CategoryResponse;
import kg.jazzlms.utils.JsonUtils;

import static kg.jazzlms.constants.Endpoints.CATEGORIES;
import static kg.jazzlms.constants.Endpoints.CATEGORIES_ID;

/** /api/categories (course service) */
public class CategoryController extends ApiRequest {

    public CategoryController(String url) {
        super(url);
    }

    public CategoryController(String url, String token) {
        super(url, token);
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        return createCategoryResponse(request).then().statusCode(201).extract().as(CategoryResponse.class);
    }

    public Response createCategoryResponse(CategoryRequest request) {
        return post(CATEGORIES, JsonUtils.toJson(request));
    }

    public Response createCategoryRaw(String body) {
        return post(CATEGORIES, body);
    }

    public Response getCategoriesResponse() {
        return get(CATEGORIES);
    }

    public Response updateCategoryResponse(Object id, CategoryRequest request) {
        return put(String.format(CATEGORIES_ID, id), JsonUtils.toJson(request));
    }

    public Response updateCategoryRaw(Object id, String body) {
        return put(String.format(CATEGORIES_ID, id), body);
    }

    public Response deleteCategoryResponse(Object id) {
        return delete(String.format(CATEGORIES_ID, id));
    }
}
