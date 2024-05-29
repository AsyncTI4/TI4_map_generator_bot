package ti4.commands.franken;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.generator.Mapper;
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
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
