package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Тело POST/PUT /api/notifications. Обязательны name (max 100), eventType, recipient, subjectTemplate, bodyTemplate.
 * recipient: RELATED_USER | ACCOUNT_OWNER | COURSE_INSTRUCTORS; delayMinutes: 0..43200.
 * В шаблонах работают плейсхолдеры вида {{firstName}}, {{lastName}}, {{courseName}}.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationRuleRequest {

    private String name;
    private String eventType;
    private String recipient;
    private Boolean active;
    private Integer delayMinutes;
    private String subjectTemplate;
    private String bodyTemplate;
}
