package kg.jazzlms.constants;

import kg.jazzlms.config.ConfigReader;

public class Endpoints {

    public static final String BASE_URL = ConfigReader.getValue("base.url");

    // user service
    public static final String USERS = "api/users";
    public static final String USERS_ID = "api/users/%s";

    // auth controller
    public static final String LOGIN = "api/auth/login";

    // course controller
    public static final String COURSES = "api/courses";
    public static final String COURSES_ID = "api/courses/%s";
    public static final String CATEGORIES = "/api/categories";
}