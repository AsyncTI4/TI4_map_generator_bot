package ti4.spring.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("discord_id")
    private String discordId;

    @JsonProperty("discord_name")
    private String discordName;

    @JsonProperty("bearer_token")
    private String bearerToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;
}
