package ti4.message;

import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.MapGenerator;

import java.util.List;

public class BotLogger {
    private static TextChannel channel;

    static {
        List<TextChannel> textChannels = MapGenerator.jda.getTextChannels();
        for (TextChannel textChannel : textChannels) {
            if ("bot-log".equals(textChannel.getName())) {
                channel = textChannel;
            }
        }
    }

    public static void log(String msg) {
        log((SlashCommandInteractionEvent) null, msg);
    }

    public static void log(SlashCommandInteractionEvent event, String msg) {
        System.out.print(msg);
        if (channel != null) {
            if (event != null) {
                String channelName = event.getChannel().getName();
                String commandString = event.getCommandString();
                channel.sendMessage(channelName + " [" + commandString + " ] " + msg).queue();
            } else {
                channel.sendMessage(msg).queue();
            }
        }
    }

    public static void log(ButtonInteractionEvent event, String msg) {
        System.out.print(msg);
        if (channel != null) {
            if (event != null) {
                String channelName = event.getChannel().getName();
                channel.sendMessage(channelName + " [button pressed] " + msg).queue();
            } else {
                channel.sendMessage(msg).queue();
            }
        }
    }
}
