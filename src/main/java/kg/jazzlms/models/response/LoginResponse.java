package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponse {

    private String token;
    private User user;
    private boolean mustChangePassword;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String username;
        private String userType;
        private String timeZone;
        private String language;
        private String bio;
        private boolean active;
        private boolean excludeFromEmails;
        private String createdAt;
        private String lastLoginAt;
    }
}