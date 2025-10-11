package ti4.website.model.stats;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.map.Player;
import ti4.model.FactionModel;

public class AbbreviatedPlayerDashboardPayload {

    private final Player player;

    AbbreviatedPlayerDashboardPayload(Player player) {
        this.player = player;
    }

    @JsonIgnore
    Player getPlayer() {
        return player;
    }

    public String getDiscordUserID() {
        return player.getUserID();
    }

    public String getDiscordUsername() {
        return player.getUserName();
    }

    public String getColor() {
        return player.getColor();
    }

    public String getFaction() {
        FactionModel factionModel = player.getFactionModel();
        if (factionModel != null) {
            return factionModel.getFactionName();
        }
        return player.getFaction();
    }

    public int getVictoryPoints() {
        return player.getTotalVictoryPoints();
    }

    public boolean isEliminated() {
        return player.isEliminated();
    }

    public boolean isRealPlayer() {
        return player.isRealPlayer();
    }

    public boolean isNpc() {
        return player.isNpc();
    }

    public int getInitiative() {
        return player.getInitiative();
    }

    public boolean isActive() {
        return player.equals(player.getGame().getActivePlayer());
    }

    public boolean isSpeaker() {
        return player.equals(player.getGame().getSpeaker());
    }
}
