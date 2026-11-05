package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Событие ленты: элемент items в GET /api/analytics/timeline */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityEventResponse {

    private String id;
    private String eventId;
    private String type;
    private String source;
    private String occurredAt;
    private String userId;
    private String description;
    private Map<String, Object> payload;
}
