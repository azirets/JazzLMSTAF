package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnitResponse {

    private UUID id;
    private UUID courseId;
    private String type;
    private String name;
    private Integer position;
    private boolean active;
    private String completionType;
    private Integer timeLimitSeconds;
    private String question;
    private String answer;
    private String sourceType;
    private String youtubeUrl;
    private String embedUrl;
    private String textContent;
    private String fileName;
    private String createdAt;
    private boolean completed;
    private String startedAt;
    private boolean autoplay;
    private boolean showSpeed;
    private String description;
    private boolean locked;
}
