package ti4.service.franken;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class FrankenStartingTechService {

    public void addStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech == null) return;
        for (String tech : startingTech) {
            addTech(event, player, tech);
        }
    }

    public void removeStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech == null) return;
        for (String tech : startingTech) {
            removeTech(event, player, tech);
        }
    }

    private void addTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.addTech(techID);
        String techRep = Mapper.getTech(techID).getRepresentation(false);
        String message = player.getRepresentation() + " added technology: " + techRep + ".";

        CommanderUnlockCheckService.checkPlayer(player, "mirveda", "jolnar", "nekro", "dihmohn");
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }

    private void removeTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.removeTech(techID);
        String techRep = Mapper.getTech(techID).getRepresentation(false);
        String message = player.getRepresentation() + " removed technology: " + techRep + ".";

        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }
}
