package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Тест как юнит курса: POST /api/courses/{id}/tests, GET/PUT /api/units/{id}/test (id юнита — unitId) */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestResponse {

    private UUID unitId;
    private UUID courseId;
    private String courseName;
    private String name;
    private boolean active;
    private Integer durationMinutes;
    private Integer passScore;
    private boolean shuffleQuestions;
    private boolean shuffleAnswers;
    private String repetitions;
    private Integer maxAttempts;
    private String showCorrectAnswers;
    private boolean showGivenAnswers;
    private boolean showLabels;
    private boolean showScore;
    private String description;
    private String messagePassed;
    private String messageFailed;
    private int questionCount;
    private int totalWeight;
    private List<QuestionResponse> questions;
}
