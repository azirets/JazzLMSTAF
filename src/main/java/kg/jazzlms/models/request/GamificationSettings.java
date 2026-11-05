package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Настройки геймификации: тело PUT /api/gamification/settings и ответ GET.
 * Один и тот же класс для запроса и ответа, поэтому лежит в request.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GamificationSettings {

    private String id;
    private Boolean enabled;
    private Points points;
    private Badges badges;
    private Levels levels;
    private Leaderboard leaderboard;

    /** Правило начисления: включено ли и сколько очков */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PointRule {
        private Boolean enabled;
        private Integer points;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder(toBuilder = true)
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Points {
        private Boolean enabled;
        private PointRule login;
        private PointRule unit;
        private PointRule course;
        private PointRule certificate;
        private PointRule test;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder(toBuilder = true)
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Badges {
        private Boolean enabled;
        private Boolean activity;
        private Boolean learning;
        private Boolean certification;
    }

    /** Правило уровня: каждые N очков / курсов / бейджей */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LevelRule {
        private Boolean enabled;
        private Integer every;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder(toBuilder = true)
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Levels {
        private Boolean enabled;
        private LevelRule byPoints;
        private LevelRule byCourses;
        private LevelRule byBadges;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder(toBuilder = true)
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Leaderboard {
        private Boolean enabled;
        private Boolean showLevels;
        private Boolean showPoints;
        private Boolean showBadges;
    }
}
