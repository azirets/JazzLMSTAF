package kg.jazzlms.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String username;
    private String password;
    private String userType;
    private String timeZone;
    private String language;
    private String bio;
    private boolean active;
    private boolean excludeFromEmails;
}