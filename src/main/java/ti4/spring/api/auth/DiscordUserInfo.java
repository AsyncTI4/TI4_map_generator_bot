package ti4.spring.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * User info response from Discord API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class DiscordUserInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("username")
    private String username;

    @JsonProperty("discriminator")
    private String discriminator;

    public String getFormattedName() {
        // Discord removed discriminators for most users (they're now "0")
        if (discriminator == null || "0".equals(discriminator)) {
            return username;
        }
        return username + "#" + discriminator;
    }
}
