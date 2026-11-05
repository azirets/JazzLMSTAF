package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Часть "unit" в POST /api/courses/{id}/units (multipart) и тело PUT /api/units/{id}.
 * name (max 80) и type обязательны.
 * type: VIDEO | PRESENTATION | CONTENT | TEST; completionType: CHECKBOX | QUESTION | TIME; sourceType: YOUTUBE | UPLOAD
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnitRequest {

    private String name;
    private String type;
    private String completionType;
    private Integer timeLimitSeconds;   // 1..86400
    private String question;            // max 500
    private String answer;              // max 200
    private String sourceType;
    private String youtubeUrl;          // max 500
    private String textContent;         // max 20000
    private Boolean active;
    private Boolean autoplay;
    private Boolean showSpeed;
    private String description;         // max 20000
}
