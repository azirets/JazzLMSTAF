package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Тело POST /api/courses/{id}/tests и PUT /api/units/{id}/test. name обязателен (max 80).
 * repetitions: ALWAYS | IF_NOT_PASSED | NEVER; showCorrectAnswers: ALWAYS | WHEN_PASSED | NEVER
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestRequest {

    private String name;
    private Boolean active;
    private Integer durationMinutes;    // 1..600
    private Integer passScore;          // 0..100
    private Boolean shuffleQuestions;
    private Boolean shuffleAnswers;
    private String repetitions;
    private Integer maxAttempts;        // >= 1
    private String showCorrectAnswers;
    private Boolean showGivenAnswers;
    private Boolean showLabels;
    private Boolean showScore;
    private String description;         // max 800
    private String messagePassed;       // max 2000
    private String messageFailed;       // max 2000
    private List<Item> questions;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Item {
        private UUID questionId;
        private Integer weight;
    }
}
