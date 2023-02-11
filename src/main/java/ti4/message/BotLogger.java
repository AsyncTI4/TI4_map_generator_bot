package ti4.message;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import ti4.MapGenerator;

import java.util.List;
import java.util.Objects;

public class BotLogger {
    private static TextChannel channel;

    static {
        Guild guild = MapGenerator.guild;
        List<TextChannel> textChannels = MapGenerator.jda.getTextChannels();
        for (TextChannel textChannel : textChannels) {
            if ("bot-log".equals(textChannel.getName()) && (textChannel.getGuild() == guild) || textChannel.getGuild().getName().equals("Bot Test Server")) {
                channel = textChannel;
            }
        }
    }

    public static void log(String msg) {
        log((SlashCommandInteractionEvent) null, msg);
    }

    public static void log(GenericCommandInteractionEvent event, String msg) {
        if (Objects.isNull(event)) {
            log(msg);
            return;
        }
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

    public static void log(SlashCommandInteractionEvent event, String msg) {
        if (Objects.isNull(event)) {
            log(msg);
            return;
        }
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
        if (Objects.isNull(event)) {
            log(msg);
            return;
        }
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
