package ti4.commands.franken;

import java.util.List;

import ti4.commands.player.AbilityInfo;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class AbilityAdd extends AbilityAddRemove {
    public AbilityAdd() {
        super(Constants.ABILITY_ADD, "Add an ability to your faction");
    }

    @Override
    public void doAction(Player player, List<String> abilityIDs) {
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, getActiveMap())).append(" added abilities:\n");
        for (String abilityID : abilityIDs ){
            if (player.hasAbility(abilityID)) {
                sb.append("> ").append(abilityID).append(" (player had this ability)");
            } else {
                sb.append("> ").append(AbilityInfo.getAbilityRepresentation(abilityID));
            }
            sb.append("\n");
            player.addAbility(abilityID);
        }
        sendMessage(sb.toString());
    }
}
