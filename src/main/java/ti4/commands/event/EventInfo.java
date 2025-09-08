package ti4.commands.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

class EventInfo extends GameStateSubcommand {

    public EventInfo() {
        super(Constants.INFO, "Send Event Cards to your #cards-info thread", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        sendEventInfo(getPlayer(), event);
        MessageHelper.sendMessageToEventChannel(event, "Event Info Sent");
    }

    public static void sendEventInfo(Player player, SlashCommandInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendEventInfo(player);
    }

    private static void sendEventInfo(Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(player, "__Events in Hand:__", getEventMessageEmbeds(player));
    }

    private static List<MessageEmbed> getEventMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (Entry<String, Integer> entry : player.getEvents().entrySet()) {
            EventModel model = Mapper.getEvent(entry.getKey());
            MessageEmbed representationEmbed = model.getRepresentationEmbed(entry.getValue());
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }
}
