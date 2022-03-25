package ti4.message;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MessageHelper {

    public static void sendMessageToChannel(MessageChannel channel, String messageText) {
        splitAndSent(messageText, channel);
    }

    public static void sendFileToChannel(MessageChannel channel, File file) {
        channel.sendFile(file).queue();
    }

    public static void replyToMessage(SlashCommandInteractionEvent event, String messageText) {
        if (messageText.length() > 1500) {
            splitAndSent(messageText, event.getChannel());
            event.reply("Message to long for replay, sent all information in base messages").queue();
        } else {
            event.reply(messageText).queue();
        }
    }

    public static void replyToMessage(SlashCommandInteractionEvent event, File file) {
        event.replyFile(file).queue();
    }

    public static void sentToMessageToUser(SlashCommandInteractionEvent event, String messageText) {
        event.getUser().openPrivateChannel().queue(channel -> {
            splitAndSent(messageText, channel);
        });
    }

    private static void splitAndSent(String messageText, MessageChannel channel) {
        if (messageText.length() > 1500) {
            List<String> texts = new ArrayList<>();
            int index = 0;
            while (index < messageText.length()) {
                texts.add(messageText.substring(index, Math.min(index + 1500, messageText.length())));
                index += 1500;
            }
            for (String text : texts) {
                channel.sendMessage(text).queue();
            }
        } else {
            channel.sendMessage(messageText).queue();
        }
    }

    public static void sentToMessageToUser(SlashCommandInteractionEvent event, String messageText, User user) {
        user.openPrivateChannel().queue(channel -> {
            splitAndSent(messageText, channel);
        });
    }
}
