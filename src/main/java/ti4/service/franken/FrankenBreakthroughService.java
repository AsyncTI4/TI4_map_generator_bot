package ti4.service.franken;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.DraftCategory;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;

@UtilityClass
public class FrankenBreakthroughService {

    public static void addBreakthrough(GenericInteractionCreateEvent event, Player player, String itemID) {
        player.addBreakthrough(itemID);
        BreakthroughModel model = Mapper.getBreakthrough(itemID);
        String msg = player.toString() + " added Breakthrough: "
                + FrankenAlternateTextService.getRepresentationWithAlternateText(
                        player.getGame(),
                        DraftCategory.BREAKTHROUGH,
                        itemID,
                        model.getNameRepresentation(),
                        model.getNameRepresentation());
        MessageHelper.sendEphemeralMessageToEventChannel(event, msg);
    }

    public static void removeBreakthrough(GenericInteractionCreateEvent event, Player player, String itemID) {
        player.removeBreakthrough(itemID);
        BreakthroughModel model = Mapper.getBreakthrough(itemID);
        String msg = player.toString() + " removed Breakthrough: "
                + FrankenAlternateTextService.getRepresentationWithAlternateText(
                        player.getGame(),
                        DraftCategory.BREAKTHROUGH,
                        itemID,
                        model.getNameRepresentation(),
                        model.getNameRepresentation());
        MessageHelper.sendEphemeralMessageToEventChannel(event, msg);
    }
}
