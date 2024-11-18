package ti4.service.franken;

import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.GenericCardModel;

@UtilityClass
public class FrankenAbilityService {

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
                for (Map.Entry<String, GenericCardModel> entry : traps.entrySet()) {
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
                    "Set destroyer max to 12 for " + player.getRepresentation() + " due to the private fleet ability");
            }
            if (abilityID.equalsIgnoreCase("industrialists")) {
                String unitID = AliasHandler.resolveUnit("spacedock");
                player.setUnitCap(unitID, 4);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Set space dock max to 4 for " + player.getRepresentation() + " due to the industrialists ability");
            }
            if (abilityID.equalsIgnoreCase("teeming")) {
                String unitID = AliasHandler.resolveUnit("dreadnought");
                player.setUnitCap(unitID, 7);
                unitID = AliasHandler.resolveUnit("mech");
                player.setUnitCap(unitID, 5);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Set dreadnought unit max to 7 and mech unit max to 5 for " + player.getRepresentation()
                        + " due to the teeming ability");
            }
            if (abilityID.equalsIgnoreCase("diplomats")) {
                ButtonHelperAbilities.resolveFreePeopleAbility(player.getGame());
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Set up free people ability markers. " + player.getRepresentationUnfogged()
                        + " any planet with the free people token on it will show up as spendable in your various spends. Once spent, the token will be removed");
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

    public static void removeAbilities(GenericInteractionCreateEvent event, Player player, List<String> abilityIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed abilities:\n");
        for (String abilityID : abilityIDs) {
            if (!player.hasAbility(abilityID)) {
                sb.append("> ").append(abilityID).append(" (player did not have this ability)");
            } else {
                sb.append("> ").append(abilityID);
            }
            sb.append("\n");
            player.removeAbility(abilityID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
