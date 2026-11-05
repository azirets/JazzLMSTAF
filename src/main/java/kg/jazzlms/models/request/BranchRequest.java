package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Тело POST/PUT /api/branches. name обязателен: [a-z0-9-]+, max 60 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BranchRequest {

    private String name;
    private String title;
    private String description;
    private String language;
    private String timeZone;
    private String defaultUserType;
    private String signupMode;
    private String announcement;
    private Boolean active;
}
