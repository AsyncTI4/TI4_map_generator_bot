package ti4.commands.search;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

@UtilityClass
class SearchHelper {

    public static void sendSearchEmbedsToEventChannel(
            SlashCommandInteractionEvent event, List<MessageEmbed> messageEmbeds) {
        if (messageEmbeds.size() > 3) {
            String threadName = event.getCommandString();
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (!messageEmbeds.isEmpty()) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            event.getChannel().sendMessage("> No results found").queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }
}
