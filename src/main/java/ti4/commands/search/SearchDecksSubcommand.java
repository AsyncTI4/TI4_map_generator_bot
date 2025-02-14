package ti4.commands.search;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.DeckModel;
import ti4.model.Source.ComponentSource;

class SearchDecksSubcommand extends SearchComponentModelSubcommand {

    public SearchDecksSubcommand() {
        super(Constants.SEARCH_DECKS, "List all decks the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidDeck(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(Mapper.getDeck(searchString).getRepresentationEmbed())
                    .queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getDecks().values().stream()
                .filter(model -> model.search(searchString, source))
                .map(DeckModel::getRepresentationEmbed)
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
