package ti4;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.helpers.Constants;
import ti4.helpers.LoggerHandler;
import ti4.map.Map;
import ti4.map.MapFileDeleter;
import ti4.map.MapManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.Set;
import java.util.StringTokenizer;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        try {
            AutoCompleteProvider.autoCompleteListener(event);
        } catch (Exception e) {
            BotLogger.log("Auto complete issue in event: " + event.getName());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        event.getInteraction().deferReply().queue();
        String userID = event.getUser().getId();
        if (!event.getInteraction().getName().equals("help")) {
            boolean isChannelOK = setActiveGame(event.getChannel(), userID, event.getName());
            if (!isChannelOK) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Command canceled. Execute command in correct channel, as game name.");
                MessageHelper.replyToMessageTI4Logo(event);
                return;
            }
        }

        //noinspection ResultOfMethodCallIgnored
//        event.deferReply();
        CommandManager commandManager = CommandManager.getInstance();
        for (Command command : commandManager.getCommandList()) {
            if (command.accept(event)) {
//                command.logBack(event);
                try {
                    command.execute(event);
                } catch (Exception e) {
                    String messageText = "Error trying to execute command: " + command.getActionID();
                    MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                    BotLogger.log(messageText);
                }
            }
        }
    }

    public static boolean setActiveGame(MessageChannel channel, String userID, String eventName) {
        String channelName = channel.getName();
        MapManager mapManager = MapManager.getInstance();
        Map userActiveMap = mapManager.getUserActiveMap(userID);
        Set<String> mapList = mapManager.getMapList().keySet();
        StringTokenizer channelNameTokenizer = new StringTokenizer(channelName, "-");


        MapFileDeleter.deleteFiles();

        String gameID = channelNameTokenizer.nextToken();
        boolean anyMatchGameExists = mapList.stream().anyMatch(map -> map.equals(gameID));
        if (!anyMatchGameExists && !(eventName.contains(Constants.CREATE_GAME) || eventName.contains(Constants.SHOW_GAME)) ) {
            return false;
        }
        if (anyMatchGameExists && (mapManager.getUserActiveMap(userID) == null || !mapManager.getUserActiveMap(userID).getName().equals(gameID) && (mapManager.getMap(gameID) != null && (mapManager.getMap(gameID).isMapOpen() || mapManager.getMap(gameID).getPlayerIDs().contains(userID))))) {
            if (mapManager.getUserActiveMap(userID) != null && !mapManager.getUserActiveMap(userID).getName().equals(gameID)) {
//                MessageHelper.sendMessageToChannel(event.getChannel(), "Active game set to: " + gameID);
            }
            mapManager.setMapForUser(userID, gameID);
        } else if (mapManager.isUserWithActiveMap(userID)) {
            if (anyMatchGameExists && !channelName.startsWith(userActiveMap.getName())) {
                MessageHelper.sendMessageToChannel(channel, "Active game reset. Channel name indicates to have map associated with it. Please select correct active game or do action in neutral channel");
                mapManager.resetMapForUser(userID);
            }
        }
        return true;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
//        CommandManager commandManager = CommandManager.getInstance();
//        for (Command command : commandManager.getCommandList()) {
//            if (command.accept(event))
//            {
//                command.execute(event);
//                String message = event.getMessage().getContentRaw();
//                LoggerHandler.logInfo(message);
//            }
//        }

        Message msg = event.getMessage();
        if (msg.getContentRaw().startsWith("map_log")) {
            if (event.isFromType(ChannelType.PRIVATE)) {
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(), event.getMessage().getContentDisplay());
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getId(), event.getMessage().getContentDisplay());
            } else {
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(), event.getTextChannel().getName(), event.getMember().getEffectiveName(), event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(), event.getTextChannel().getId(), event.getAuthor().getId(), event.getMessage().getContentDisplay());
            }
        }
    }
}