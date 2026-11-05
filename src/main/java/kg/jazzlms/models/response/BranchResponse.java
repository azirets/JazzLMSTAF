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
public class BranchResponse {

    private UUID id;
    private String name;
    private String title;
    private String description;
    private String language;
    private String timeZone;
    private String defaultUserType;
    private String signupMode;
    private String announcement;
    private boolean active;
    private String createdAt;
}
