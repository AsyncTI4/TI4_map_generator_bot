package ti4.service.statistics;

import java.io.File;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.message.MessageHelper;

@UtilityClass
public class StatisticsThreadHelper {

    private static final int MAX_THREAD_NAME_LENGTH = 100;

    public static void sendMessage(SlashCommandInteractionEvent event, String message) {
        MessageHelper.sendMessageToThread(event.getChannel(), getThreadName(event), message);
    }

    public static void sendFile(SlashCommandInteractionEvent event, File file) {
        MessageHelper.sendFileToThread(event.getChannel(), getThreadName(event), file);
    }

    public static String getThreadName(SlashCommandInteractionEvent event) {
        return StringUtils.abbreviate(event.getFullCommandName(), MAX_THREAD_NAME_LENGTH);
    }
}
