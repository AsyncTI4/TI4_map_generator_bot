package ti4.service.franken;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.GenericCardModel;

@UtilityClass
public class FrankenPlotService {

    public static void addPlot(GenericInteractionCreateEvent event, Player player, String itemID) {
        player.setPlotCard(itemID);
        GenericCardModel model = Mapper.getPlot(itemID);
        String msg = player.getRepresentation() + " added Plot Card: " + model.getNameRepresentation();
        MessageHelper.sendEphemeralMessageToEventChannel(event, msg);
    }

    public static void removePlot(GenericInteractionCreateEvent event, Player player, String itemID) {
        player.getPlotCardsRaw().remove(itemID);
        GenericCardModel model = Mapper.getPlot(itemID);
        String msg = player.getRepresentation() + " removed Plot Card: " + model.getNameRepresentation();
        MessageHelper.sendEphemeralMessageToEventChannel(event, msg);
    }
}
