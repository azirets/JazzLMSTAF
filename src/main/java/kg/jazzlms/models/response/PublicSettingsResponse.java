package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET /api/auth/settings (публичный) */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicSettingsResponse {

    private String siteName;
    private boolean signupAllowed;
    private String termsOfService;
    private String visibleUserFormat;
}
