package ti4.service.tigl;

import lombok.Data;

@Data
public class TiglUsernameChangeRequest {
    private String discordId;
    private String newTiglUserName;
}
