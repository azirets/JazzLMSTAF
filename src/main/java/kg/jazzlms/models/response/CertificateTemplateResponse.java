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
public class CertificateTemplateResponse {

    private UUID id;
    private String name;
    private String background;
    private String title;
    private String body;
    private String signature;
    private String createdAt;
    private String updatedAt;
    @com.fasterxml.jackson.annotation.JsonProperty("default")
    private boolean isDefault;
}
