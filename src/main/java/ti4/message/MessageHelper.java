package ti4.message;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.io.File;

public class MessageHelper {

    public static void sendMessageToChannel(MessageChannel channel, String messageText)
    {
        channel.sendMessage(messageText).queue();
    }

    public static void sendFileToChannel(MessageChannel channel, File file)
    {
        channel.sendFile(file).queue();
    }

    public static void replyToMessage(SlashCommandInteractionEvent event, String messageText)
    {
        event.reply(messageText).queue();
    }
    public static void replyToMessage(SlashCommandInteractionEvent event, File file)
    {
        event.replyFile(file).queue();
    }

    public static void sentToMessageToUser(SlashCommandInteractionEvent event, String messageText)
    {
        event.getUser().openPrivateChannel().queue(channel -> {
            channel.sendMessage(messageText).queue();
        });
    }
}
