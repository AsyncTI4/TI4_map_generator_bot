package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.map.Player;

public class TechRemove extends TechAddRemove {
    public TechRemove() {
        super(Constants.TECH_REMOVE, "Remove Tech");
    }

    @Override
    public void doAction(Player player, String techID) {
        player.removeTech(techID);
    }
}
