package ti4.service.tigl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TiglPlayerResult {
    private int score;
    private String faction;
    private Long discordId;
    private String discordTag;

    @JsonProperty("isWinner")
    private boolean winner;
}
