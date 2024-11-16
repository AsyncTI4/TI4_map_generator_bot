package ti4.helpers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

@UtilityClass
public class EventHelper {

    public static void revealEvent(GenericInteractionCreateEvent event, Game game, MessageChannel channel) {
        revealEvent(event, game, channel, game.revealEvent(false));
    }

    public static void revealEvent(GenericInteractionCreateEvent event, Game game, MessageChannel channel, String eventID) {
        EventModel eventModel = Mapper.getEvent(eventID);
        if (eventModel != null) {
            channel.sendMessageEmbeds(eventModel.getRepresentationEmbed()).queue();
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Something went wrong revealing an event; eventID: " + eventID);
        }
    }
}
