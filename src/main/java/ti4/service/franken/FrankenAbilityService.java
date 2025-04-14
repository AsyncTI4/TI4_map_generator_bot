package ti4.service.franken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.Constants;
import ti4.image.Mapper;
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
                    "Set destroyer max to 12 for " + player.getRepresentation() + " due to the **Private Fleet** ability.");
            }
            if (abilityID.equalsIgnoreCase("policies")) {
                player.removeAbility("policies");
                player.addAbility("policy_the_people_connect");
                player.addAbility("policy_the_environment_preserve");
                player.addAbility("policy_the_economy_empower");
                player.removeOwnedUnitByID("olradin_mech");
                player.addOwnedUnitByID("olradin_mech_positive");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() + " automatically set all of your policies to the positive side, but you can flip any of them now with these buttons");
                ButtonHelperHeroes.offerOlradinHeroFlips(player.getGame(), player);
                ButtonHelperHeroes.offerOlradinHeroFlips(player.getGame(), player);
                ButtonHelperHeroes.offerOlradinHeroFlips(player.getGame(), player);
            }
            if (abilityID.equalsIgnoreCase("industrialists")) {
                String unitID = AliasHandler.resolveUnit("spacedock");
                player.setUnitCap(unitID, 4);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Set space dock max to 4 for " + player.getRepresentation() + " due to the **Industrialists** ability.");
            }
            if (abilityID.equalsIgnoreCase("teeming")) {
                String unitID = AliasHandler.resolveUnit("dreadnought");
                player.setUnitCap(unitID, 7);
                unitID = AliasHandler.resolveUnit("mech");
                player.setUnitCap(unitID, 5);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Set dreadnought unit max to 7 and mech unit max to 5 for " + player.getRepresentation()
                        + " due to the **Teeming** ability.");
            }
            if (abilityID.equalsIgnoreCase("diplomats")) {
                ButtonHelperAbilities.resolveFreePeopleAbility(player.getGame());
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Set up **Free People** ability markers. " + player.getRepresentationUnfogged()
                        + ", any planet with a **Free People** token on it will show up as spendable in your various spends. Once spent, the token will be removed.");
            }
            if (abilityID.equalsIgnoreCase("the_lady_and_the_lord")) {
                player.addOwnedUnitByID("ghemina_flagship_lady");
            }
            if (abilityID.equalsIgnoreCase("ancient_empire")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("startAncientEmpire", "Place a tomb token"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation() + " You can use this button to place 14 tomb tokens.", buttons);
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
