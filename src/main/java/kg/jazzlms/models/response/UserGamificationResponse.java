package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** GET /api/gamification/me и /users/{id}: очки, уровень, бейджи и история начислений */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserGamificationResponse {

    private String userId;
    private boolean enabled;
    private int points;
    private int level;
    private int nextLevelPoints;
    private int logins;
    private int units;
    private int courses;
    private List<Map<String, Object>> badges;
    private List<Map<String, Object>> history;
    private Integer rank;
    private Integer levelRank;
    private Integer badgeRank;
}
