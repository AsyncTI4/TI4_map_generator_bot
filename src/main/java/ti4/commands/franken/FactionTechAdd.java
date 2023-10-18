package ti4.commands.franken;

import java.util.List;

import ti4.helpers.Constants;
import ti4.helpers.Helper;

import ti4.map.Player;

public class FactionTechAdd extends FactionTechAddRemove {
    public FactionTechAdd() {
        super(Constants.FACTION_TECH_ADD, "Add a faction tech to your faction");
    }

    @Override
    public void doAction(Player player, List<String> techIDs) {
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, getActiveGame())).append(" added techs:\n");
        for (String techID : techIDs) {
            if (player.getFactionTechs().contains(techID)) {
                sb.append("> ").append(techID).append(" (player had this faction tech)");
            } else {
                sb.append("> ").append(Helper.getTechRepresentation(techID));
            }
            sb.append("\n");
            player.addFactionTech(techID);
        }
        sendMessage(sb.toString());
    }
}
