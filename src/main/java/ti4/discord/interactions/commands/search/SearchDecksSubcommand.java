package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.DeckModel;

class SearchDecksSubcommand extends SearchComponentModelSubcommand<DeckModel> {

    SearchDecksSubcommand() {
        super(
                Constants.SEARCH_DECKS,
                "List all decks the bot can use",
                Mapper::isValidDeck,
                Mapper::getDeck,
                valuesOf(() -> Mapper.getDecks().values()),
                DeckModel::getRepresentationEmbed,
                null);
    }
}
