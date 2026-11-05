package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Тело PUT /api/settings (он же ответ GET). Обязательны siteName, defaultLanguage, defaultTimeZone, lockMinutes >= 1 */
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsRequest {

    private String siteName;
    private String siteDescription;
    private String defaultLanguage;
    private String defaultTimeZone;
    private String signupMode;
    private String defaultUserType;
    private String defaultGroupId;
    private Integer passwordMaxAgeDays;
    private Boolean passwordChangeOnFirstLogin;
    private Integer lockAfterAttempts;
    private Integer lockMinutes;
    private String termsOfService;
    private String visibleUserFormat;
}
