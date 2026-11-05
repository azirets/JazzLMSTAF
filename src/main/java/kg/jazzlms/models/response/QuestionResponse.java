package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionResponse {

    private UUID id;
    private UUID courseId;
    private String courseName;
    private String type;
    private String text;
    private Map<String, Object> data;
    private String feedback;
    private String tags;
    private String createdAt;
    private int usedInTests;
}
