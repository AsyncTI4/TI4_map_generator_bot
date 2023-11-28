package ti4.commands.event;

import java.util.Map;
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

import java.util.LinkedHashMap;

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
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getEventInfo(activeGame, player));
    }

    private static String getEventInfo(Game activeGame, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");

        //ACTION CARDS
        sb.append("**Event Cards in hand:**").append("\n");
        int index = 1;

        LinkedHashMap<String, Integer> eventCards = player.getEvents();
        if (eventCards != null) {
            if (eventCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> event : eventCards.entrySet()) {
                    Integer value = event.getValue();
                    EventModel eventModel = Mapper.getEvent(event.getKey());

                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                    if (eventModel == null) {
                        sb.append("Something broke here");
                    } else {
                        sb.append(eventModel.getRepresentation(value));
                    }

                    index++;
                }
            }
        }
        return sb.toString();
    }
}
