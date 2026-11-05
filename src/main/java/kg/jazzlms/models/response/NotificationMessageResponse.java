package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Письмо: GET /api/notifications/pending и /history. status: PENDING | SENT | FAILED */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationMessageResponse {

    private String id;
    private String ruleId;
    private String ruleName;
    private String eventType;
    private String recipientEmail;
    private String subject;
    private String body;
    private String status;
    private String createdAt;
    private String scheduledAt;
    private String sentAt;
}
