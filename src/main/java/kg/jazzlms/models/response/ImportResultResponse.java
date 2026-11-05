package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /api/users/import: { "created": N, "skipped": [{ line, value, reason }] } */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportResultResponse {

    private int created;
    private List<Skipped> skipped;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Skipped {
        private int line;
        private String value;
        private String reason;
    }
}
