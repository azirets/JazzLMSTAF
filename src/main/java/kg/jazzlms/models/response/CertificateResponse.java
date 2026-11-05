package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Выданный сертификат: GET /api/certificates/{id}, /user/{userId}, /my */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateResponse {

    private UUID id;
    private String code;
    private String issuedAt;
    private UUID courseId;
    private String courseName;
    private UUID templateId;
}
