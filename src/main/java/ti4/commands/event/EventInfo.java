package ti4.commands.event;

import java.util.Map.Entry;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

import java.util.ArrayList;
import java.util.List;

public class EventInfo extends EventSubcommandData {
    public EventInfo() {
        super(Constants.INFO, "Send Event Cards to your Cards Info thread");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        sendEventInfo(activeGame, player, event);
        sendMessage("Event Info Sent");
    }

    public static void sendEventInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = player.getRepresentation(true, true) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendEventInfo(activeGame, player);
    }

    public static void sendEventInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation(true, true) + " used something";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendEventInfo(activeGame, player);
    }

    public static void sendEventInfo(Game activeGame, Player player, ButtonInteractionEvent event) {
        String headerText = player.getRepresentation() + " pressed button: " + event.getButton().getLabel();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendEventInfo(activeGame, player);
    }

    public static void sendEventInfo(Game activeGame, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, "_ _\n__**Events in Hand:**__", getEventMessageEmbeds(player));
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
