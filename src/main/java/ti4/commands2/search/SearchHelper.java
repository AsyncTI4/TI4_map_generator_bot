package ti4.commands2.search;

import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.message.MessageHelper;

class SearchHelper {

    public static void sendSearchEmbedsToEventChannel(SlashCommandInteractionEvent event, List<MessageEmbed> messageEmbeds) {
        if (messageEmbeds.size() > 3) {
            String threadName = event.getCommandString();
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (!messageEmbeds.isEmpty()) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue();
        } else {
            event.getChannel().sendMessage("> No results found").queue();
        }
    }
}
