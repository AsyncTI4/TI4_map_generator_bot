package ti4.commands.franken;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.GenericCardModel;

public class AbilityAdd extends AbilityAddRemove {
    public AbilityAdd() {
        super(Constants.ABILITY_ADD, "Add an ability to your faction");
    }

    @Override
    public void doAction(Player player, List<String> abilityIDs) {
        addAbilities(getEvent(), player, abilityIDs);
    }

    public static void addAbilities(GenericInteractionCreateEvent event, Player player, List<String> abilityIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added abilities:\n");
        for (String abilityID : abilityIDs) {
            if (!Mapper.isValidAbility(abilityID)) continue;
            if (player.hasAbility(abilityID)) {
                sb.append("> ").append(abilityID).append(" (player had this ability)");
            } else {
                AbilityModel abilityModel = Mapper.getAbility(abilityID);
                sb.append("> ").append(abilityModel.getRepresentation());
            }
            sb.append("\n");
            player.addAbility(abilityID);
            if (abilityID.equalsIgnoreCase("cunning")) {
                Map<String, GenericCardModel> traps = Mapper.getTraps();
                for (Entry<String, GenericCardModel> entry : traps.entrySet()) {
                    String key = entry.getKey();
                    if (key.endsWith(Constants.LIZHO)) {
                        player.setTrapCard(key);
                    }
                }
            }
            if (abilityID.equalsIgnoreCase("private_fleet")) {
                String unitID = AliasHandler.resolveUnit("destroyer");
                player.setUnitCap(unitID, 12);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Added 4 additional destroyers (total of 12) to the reinforcements of " + player.getRepresentation() + " due to the Private Fleet faction ability.");
            }
            if (abilityID.equalsIgnoreCase("industrialists")) {
                String unitID = AliasHandler.resolveUnit("spacedock");
                player.setUnitCap(unitID, 4);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Added 1 additional space dock (total of 4) to the reinforcements of " + player.getRepresentation() + " due to the Industrialists faction ability.");
            }
            if (abilityID.equalsIgnoreCase("teeming")) {
                String unitID = AliasHandler.resolveUnit("dreadnought");
                player.setUnitCap(unitID, 7);
                unitID = AliasHandler.resolveUnit("mech");
                player.setUnitCap(unitID, 5);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Added 2 additional dreadnoughts (total of 7) and 1 additional mech (total of 5) to the reinforcements of "
                    + player.getRepresentation() + " due to the Teeming faction ability.");
            }
            if (abilityID.equalsIgnoreCase("diplomats")) {
                ButtonHelperAbilities.resolveFreePeopleAbility(player.getGame());
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Set up Free People faction ability markers. " + player.getRepresentation(true, true)
                        + " any planet with the Free People token on it will show up as spendable in your various spends. Once spent, the token will be removed.");
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
