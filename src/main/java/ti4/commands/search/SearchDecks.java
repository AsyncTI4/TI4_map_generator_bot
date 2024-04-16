package ti4.commands.search;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;

public class SearchDecks extends SearchSubcommandData {

    public SearchDecks() {
        super(Constants.SEARCH_DECKS, "List all decks the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);

        if (Mapper.isValidDeck(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getDeck(searchString).getRepresentationEmbed()).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        for (DeckModel model : Mapper.getDecks().values()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed();
            if (Helper.embedContainsSearchTerm(representationEmbed, searchString)) messageEmbeds.add(representationEmbed);
        }
        if (messageEmbeds.size() > 1) {
            String threadName = event.getFullCommandName() + (searchString == null ? "" : " search: " + searchString);
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (messageEmbeds.size() > 0) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue();
        }
    }
}
