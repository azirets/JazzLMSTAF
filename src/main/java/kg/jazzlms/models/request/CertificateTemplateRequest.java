package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Тело POST/PUT /api/certificates/templates. Обязательны name (100), background, title (200), body (5000); signature max 100 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateTemplateRequest {

    private String name;
    private String background;
    private String title;
    private String body;
    private String signature;
}
