package ti4.map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import ti4.message.BotLogger;

public class PlayerStatsDashboardPayload {

    private final Player player;

    public PlayerStatsDashboardPayload(Player player) {
        this.player = player;
    }

    @JsonIgnore
    public String getJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            BotLogger.log("Could not get PlayerStatsDashboardPayload JSON for Game: " + player.getGame().getID() + " Player: " + player.getUserName(), e);
            return null;
        }
    }
}
