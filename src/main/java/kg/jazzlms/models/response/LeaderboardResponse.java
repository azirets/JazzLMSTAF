package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** GET /api/gamification/leaderboard: { by, top: [...], me: {...} } */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeaderboardResponse {

    private String by;
    private List<Entry> top;
    private Entry me;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private int rank;
        private String userId;
        private String name;
        private long score;
        private boolean me;
    }
}
