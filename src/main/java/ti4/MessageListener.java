package ti4;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.LoggerHandler;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getFocusedOption().getName().equals(Constants.COLOR)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Mapper.getColors().stream()
                    .limit(25)
                    .filter(color -> color.startsWith(enteredValue))
                    .map(color -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(color, color))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        } else if (event.getFocusedOption().getName().equals(Constants.TOKEN)) {
            String enteredValue = event.getFocusedOption().getValue();
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> options = Mapper.getTokens().stream()
                    .limit(25)
                    .filter(token -> token.startsWith(enteredValue))
                    .map(token -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(token, token))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        if (mapManager.isUserWithActiveMap(userID)) {
            String channelName = event.getChannel().getName();
            Map userActiveMap = mapManager.getUserActiveMap(userID);
            Set<String> mapList = mapManager.getMapList().keySet();
            if (mapList.stream().anyMatch(channelName::startsWith) && !channelName.startsWith(userActiveMap.getName())) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Active map reset. Channel name indicates to have map associated with it. Please select correct active map or do action in neutral channel");
                mapManager.resetMapForUser(userID);
            }
        }

        //noinspection ResultOfMethodCallIgnored
//        event.deferReply();
        CommandManager commandManager = CommandManager.getInstance();
        for (Command command : commandManager.getCommandList()) {
            if (command.accept(event)) {
                command.logBack(event);
                try {
                    command.execute(event);
                } catch (Exception e) {
                    String messageText = "Error trying to execute command: " + command.getActionID();
                    MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                    LoggerHandler.log(messageText, e);
                }
            }
        }
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
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(),
                        event.getMessage().getContentDisplay());
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getId(),
                        event.getMessage().getContentDisplay());
            } else {
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(),
                        event.getTextChannel().getName(), event.getMember().getEffectiveName(),
                        event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(),
                        event.getTextChannel().getId(), event.getAuthor().getId(),
                        event.getMessage().getContentDisplay());
            }
        }
    }
}