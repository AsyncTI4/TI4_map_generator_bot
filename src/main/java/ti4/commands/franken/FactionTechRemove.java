package ti4.commands.franken;

import java.util.List;

import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class FactionTechRemove extends FactionTechAddRemove {
    public FactionTechRemove() {
        super(Constants.FACTION_TECH_REMOVE, "Remove a faction tech from your faction");
    }
    
    @Override
    public void doAction(Player player, List<String> techIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed faction techs:\n");
        for (String techID : techIDs ){
            if (!player.getFactionTechs().contains(techID)) {
                sb.append("> ").append(techID).append(" (player did not have this tech)");
            } else {
                sb.append("> ").append(techID);
            }
            sb.append("\n");
            player.removeFactionTech(techID);
        }
        MessageHelper.sendMessageToEventChannel(getEvent(), sb.toString());
    }
}
