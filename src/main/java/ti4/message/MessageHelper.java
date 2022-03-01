package ti4.message;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.io.File;

public class MessageHelper {

    public static void sendMessageToChannel(MessageChannel channel, String messageText)
    {
        channel.sendMessage(messageText).queue();
    }

    public static void replyToMessage(Message message, String messageText)
    {
        message.reply(messageText).queue();
    }
    public static void replyToMessage(Message message, File file)
    {
        message.reply(file).queue();
    }
}
