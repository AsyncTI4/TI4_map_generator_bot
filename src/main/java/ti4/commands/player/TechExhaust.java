package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.map.Player;

public class TechExhaust extends TechAddRemove {
    public TechExhaust() {
        super(Constants.TECH_EXHAUST, "Exhaust Tech");
    }

    @Override
    public void doAction(Player player, String techID) {
        player.exhaustTech(techID);
    }
}
