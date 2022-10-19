package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.map.Player;

public class TechRefresh extends TechAddRemove {
    public TechRefresh() {
        super(Constants.TECH_REFRESH, "Ready Tech");
    }

    @Override
    public void doAction(Player player, String techID) {
        player.refreshTech(techID);
    }
}
