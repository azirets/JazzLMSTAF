package kg.jazzlms.constants;

import kg.jazzlms.config.ConfigReader;

public class Endpoints {

    public static final String BASE_URL = ConfigReader.getValue("base.url");
    public static final String COURSE_URL = ConfigReader.getValue("course.url");
    public static final String GATEWAY_URL = ConfigReader.getValue("gateway.url");
    public static final String NOTIFICATION_URL = ConfigReader.getValue("notification.url");
    public static final String ANALYTICS_URL = ConfigReader.getValue("analytics.url");
    public static final String GAMIFICATION_URL = ConfigReader.getValue("gamification.url");

    // notification service
    public static final String NOTIFICATIONS = "api/notifications";
    public static final String NOTIFICATIONS_ID = "api/notifications/%s";
    public static final String NOTIFICATIONS_RECIPIENTS = "api/notifications/recipients";
    public static final String NOTIFICATIONS_EVENTS = "api/notifications/events";
    public static final String NOTIFICATIONS_PENDING = "api/notifications/pending";
    public static final String NOTIFICATIONS_HISTORY = "api/notifications/history";
    public static final String NOTIFICATIONS_MESSAGES_ID = "api/notifications/messages/%s";

    // analytics service
    public static final String ANALYTICS_SUMMARY = "api/analytics/summary";
    public static final String ANALYTICS_OVERVIEW = "api/analytics/overview";
    public static final String ANALYTICS_DASHBOARD = "api/analytics/dashboard";
    public static final String ANALYTICS_TOP_USERS = "api/analytics/top-users";
    public static final String ANALYTICS_TIMELINE = "api/analytics/timeline";
    public static final String ANALYTICS_TIMELINE_EXPORT = "api/analytics/timeline/export";
    public static final String ANALYTICS_USER_ACTIVITY = "api/analytics/users/%s/activity";
    public static final String ANALYTICS_COURSE_ACTIVITY = "api/analytics/courses/%s/activity";

    // gamification service
    public static final String GAMIFICATION_SETTINGS = "api/gamification/settings";
    public static final String GAMIFICATION_SETTINGS_RESET = "api/gamification/settings/reset";
    public static final String GAMIFICATION_RESET_STATISTICS = "api/gamification/reset-statistics";
    public static final String GAMIFICATION_CONFIG = "api/gamification/config";
    public static final String GAMIFICATION_RULES = "api/gamification/rules";
    public static final String GAMIFICATION_ME = "api/gamification/me";
    public static final String GAMIFICATION_USERS_ID = "api/gamification/users/%s";
    public static final String GAMIFICATION_LEADERBOARD = "api/gamification/leaderboard";

    // user service
    public static final String USERS = "api/users";
    public static final String USERS_ID = "api/users/%s";

    // auth controller
    public static final String LOGIN = "api/auth/login";
    public static final String ME = "api/auth/me";
    public static final String AUTH_REGISTER = "api/auth/register";
    public static final String AUTH_PASSWORD = "api/auth/password";
    public static final String AUTH_IMPERSONATE = "api/auth/impersonate/%s";
    public static final String AUTH_SETTINGS = "api/auth/settings";

    // user service: users, groups, branches, settings, demo
    public static final String USERS_STATS = "api/users/stats";
    public static final String USERS_EXPORT = "api/users/export";
    public static final String USERS_IMPORT = "api/users/import";
    public static final String USERS_GROUPS = "api/users/%s/groups";
    public static final String USERS_BRANCHES = "api/users/%s/branches";
    public static final String SETTINGS = "api/settings";
    public static final String GROUPS = "api/groups";
    public static final String GROUPS_ID = "api/groups/%s";
    public static final String GROUPS_USERS = "api/groups/%s/users";
    public static final String GROUPS_USER = "api/groups/%s/users/%s";
    public static final String BRANCHES = "api/branches";
    public static final String BRANCHES_ID = "api/branches/%s";
    public static final String BRANCHES_USERS = "api/branches/%s/users";
    public static final String BRANCHES_USER = "api/branches/%s/users/%s";
    public static final String DEMO_STATUS = "api/demo/status/%s";

    // course controller
    public static final String COURSES = "api/courses";
    public static final String COURSES_ID = "api/courses/%s";
    public static final String CATEGORIES = "api/categories";
    public static final String COURSES_CATALOG = "api/courses/catalog";
    public static final String COURSES_SELF_ENROLL = "api/courses/%s/enroll";
    public static final String COURSES_ENROLLMENTS = "api/courses/%s/enrollments";
    public static final String COURSES_PERMANENT = "api/courses/%s/permanent";

    // enrollments
    public static final String ENROLLMENTS_MY = "api/enrollments/my";
    public static final String ENROLLMENTS_ID = "api/enrollments/%s";
    public static final String ENROLLMENTS_PROGRESS = "api/enrollments/%s/progress";
    public static final String ENROLLMENTS_USER = "api/enrollments/user/%s";
    public static final String ENROLLMENTS_MY_STATS = "api/enrollments/my/stats";

    // course controller: lifecycle, reports
    public static final String COURSES_STATS = "api/courses/stats";
    public static final String COURSES_DELETED = "api/courses/deleted";
    public static final String COURSES_RESTORE = "api/courses/%s/restore";
    public static final String COURSES_CLONE = "api/courses/%s/clone";
    public static final String COURSES_FILES = "api/courses/%s/files";
    public static final String COURSES_REPORT_USERS = "api/courses/%s/report/users";
    public static final String COURSES_REPORT_OVERVIEW = "api/courses/%s/report/overview";
    public static final String COURSES_REPORT_MATRIX = "api/courses/%s/report/matrix";

    // categories
    public static final String CATEGORIES_ID = "api/categories/%s";

    // certificates
    public static final String CERT_TEMPLATES = "api/certificates/templates";
    public static final String CERT_TEMPLATES_ID = "api/certificates/templates/%s";
    public static final String CERT_TEMPLATES_RESET = "api/certificates/templates/%s/reset";
    public static final String CERT_TEMPLATES_PREVIEW = "api/certificates/templates/%s/preview";
    public static final String CERT_BACKGROUNDS = "api/certificates/templates/backgrounds";
    public static final String CERT_ID = "api/certificates/%s";
    public static final String CERT_VIEW = "api/certificates/%s/view";
    public static final String CERT_USER = "api/certificates/user/%s";
    public static final String CERT_MY = "api/certificates/my";

    // units (lessons), tests, attempts
    public static final String COURSES_UNITS = "api/courses/%s/units";
    public static final String COURSES_TESTS = "api/courses/%s/tests";
    public static final String UNITS_ID = "api/units/%s";
    public static final String UNITS_START = "api/units/%s/start";
    public static final String UNITS_MOVE = "api/units/%s/move";
    public static final String UNITS_COMPLETE = "api/units/%s/complete";
    public static final String UNITS_TEST = "api/units/%s/test";
    public static final String UNITS_ATTEMPTS = "api/units/%s/test/attempts";
    public static final String UNITS_ATTEMPT = "api/units/%s/test/attempts/%s";
    public static final String UNITS_ATTEMPT_SUBMIT = "api/units/%s/test/attempts/%s/submit";
    public static final String UNITS_FILES_USER = "api/units/files/%s";

    // question bank
    public static final String COURSES_QUESTIONS = "api/courses/%s/questions";
    public static final String COURSES_QUESTIONS_IMPORT = "api/courses/%s/questions/import";
    public static final String QUESTIONS_ID = "api/questions/%s";

}
