package ti4;

import net.dv8tion.jda.api.entities.channel.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Storage;
import ti4.map.Map;
import ti4.map.MapFileDeleter;
import ti4.map.MapManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.exception.ExceptionUtils;

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
        Member member = event.getMember();
        if (member != null) event.getChannel().sendMessage("```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```").queue();

        // CHECK IF CHANNEL IS MATCHED TO A GAME
        if (!event.getInteraction().getName().equals("help")) { //SKIP /help COMMANDS
            boolean isChannelOK = setActiveGame(event.getChannel(), userID, event.getName());
            if (!isChannelOK) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Command canceled. Execute command in correct channel, as game name.");
                MessageHelper.replyToMessageTI4Logo(event);
                return;
            }
        }

        CommandManager commandManager = CommandManager.getInstance();
        for (Command command : commandManager.getCommandList()) {
            if (command.accept(event)) {
                try {
                    command.execute(event);
                    command.postExecute(event);
                } catch (Exception e) {
                    String messageText = "Error trying to execute command: " + command.getActionID();
                    String errorMessage = ExceptionUtils.getMessage(e);
                    event.getHook().editOriginal(errorMessage).queue();
                    BotLogger.log(event, messageText, e);
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
        if (!anyMatchGameExists && !(eventName.contains(Constants.SHOW_GAME) || eventName.contains(Constants.CREATE_GAME) || eventName.contains(Constants.BOTHELPER) || eventName.contains(Constants.ADMIN))) {
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
        if (msg.getContentRaw().startsWith("[DELETE]")) {
            msg.delete().queue();
        }
        if (msg.getContentRaw().startsWith("map_log")) {
            if (event.isFromType(ChannelType.PRIVATE)) {
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(), event.getMessage().getContentDisplay());
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getId(), event.getMessage().getContentDisplay());
            } else {
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(), event.getChannel().asTextChannel().getName(), event.getMember().getEffectiveName(), event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(), event.getChannel().asTextChannel().getId(), event.getAuthor().getId(), event.getMessage().getContentDisplay());
            }
        }

        // TTPG-EXPORTS - Save attachment to ttpg_exports folder for later processing
        if (event.getChannel().getName().equalsIgnoreCase("ttpg-exports")) {
            List<Message.Attachment> attachments = event.getMessage().getAttachments();
            if (attachments.isEmpty()) {
                return; // no attachments on the message!
            } else if (!attachments.get(0).getFileExtension().equalsIgnoreCase("json")) {
                // MessageHelper.sendMessageToChannel(event.getChannel(), "File is not a JSON file. Will not be saved.");
                return;
            } else { //write to file
                String currentDateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmmss"));
                String fileName = "ttpgexport_" + currentDateTime + ".json";
                String filePath =  Storage.getTTPGExportDirectory() + "/" + fileName;
                File file = new File(filePath);
                CompletableFuture<File> future = attachments.get(0).getProxy().downloadToFile(file);
                future.exceptionally(error -> { // handle possible errors
                    error.printStackTrace();
                    return null;
                });
                MessageHelper.sendMessageToChannel(event.getChannel(), "File imported as: `" + fileName + "`");
            }
        }
    }
}