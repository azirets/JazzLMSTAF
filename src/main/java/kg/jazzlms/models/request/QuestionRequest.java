package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Тело POST /api/courses/{id}/questions и PUT /api/questions/{id}. text (max 5000) и type обязательны.
 * type: MULTIPLE_CHOICE | FILL_GAP | ORDERING | DRAG_DROP | FREE_TEXT | RANDOMIZED.
 * data зависит от типа (см. EntityManager.questionBuilder и тесты).
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionRequest {

    private String type;
    private String text;
    private Map<String, Object> data;
    private String feedback;            // max 2000
    private String tags;                // max 255
}
