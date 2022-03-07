package ti4;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.generator.GenerateMap;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.helpers.LoggerHandler;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
    {
//        Member member = event.getMember();
//        String logText = (member == null ? "NA" : member.getEffectiveName() + " " +member.getId())
//        LoggerHandler.log(member.getId());
        CommandManager commandManager = CommandManager.getInstance();
        for (Command command : commandManager.getCommandList()) {
            if (command.accept(event))
            {
                command.logBack(event);
                command.execute(event);
//                String message = event.getMessage().getContentRaw();
//                LoggerHandler.logInfo(message);
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