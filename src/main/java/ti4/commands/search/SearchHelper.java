package ti4.commands.search;

import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.message.MessageHelper;

public class SearchHelper {
    public static void sendSearchEmbedsToEventChannel(SlashCommandInteractionEvent event, List<MessageEmbed> messageEmbeds) {
        if (messageEmbeds.size() > 3) {
            String threadName = event.getCommandString();
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (messageEmbeds.size() > 0) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue();
        } else {
            event.getChannel().sendMessage("> No results found").queue();
        }
    }
}
