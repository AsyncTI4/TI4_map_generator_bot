package ti4.commands.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

public class EventInfo extends PlayerGameStateSubcommand {

    public EventInfo() {
        super(Constants.INFO, "Send Event Cards to your Cards Info thread", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        sendEventInfo(game, player, event);
        MessageHelper.sendMessageToEventChannel(event, "Event Info Sent");
    }

    public static void sendEventInfo(Game game, Player player, SlashCommandInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendEventInfo(game, player);
    }

    public static void sendEventInfo(Game game, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "_ _\n__**Events in Hand:**__", getEventMessageEmbeds(player));
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
