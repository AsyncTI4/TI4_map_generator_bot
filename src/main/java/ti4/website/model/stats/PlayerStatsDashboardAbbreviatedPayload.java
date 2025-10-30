package ti4.website.model.stats;

import ti4.map.Player;

public class PlayerStatsDashboardAbbreviatedPayload {

    private final Player player;

    PlayerStatsDashboardAbbreviatedPayload(Player player) {
        this.player = player;
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

    public String getFactionName() {
        var factionModel = player.getFactionModel();
        return factionModel == null ? null : factionModel.getFactionName();
    }

    public int getScore() {
        return player.getTotalVictoryPoints();
    }

    public boolean isEliminated() {
        return player.isEliminated();
    }

    public boolean isRealPlayer() {
        return player.isRealPlayer();
    }
}
