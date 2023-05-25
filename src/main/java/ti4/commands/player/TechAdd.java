package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class TechAdd extends TechAddRemove {
    public TechAdd() {
        super(Constants.TECH_ADD, "Add Tech");
    }

    @Override
    public void doAction(Player player, String techID) {
        player.addTech(techID);
        sendMessage(Helper.getPlayerRepresentation(player, getActiveMap()) + " added tech: " + Helper.getTechRepresentation(techID));
    }
}
