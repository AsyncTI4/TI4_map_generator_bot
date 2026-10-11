package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;

class SearchActionCardsSubcommand extends SearchComponentModelSubcommand<ActionCardModel> {

    SearchActionCardsSubcommand() {
        super(
                Constants.SEARCH_ACTION_CARDS,
                "List all action cards the bot can use",
                Mapper::isValidActionCard,
                Mapper::getActionCard,
                valuesOf(() -> Mapper.getActionCards().values()),
                model -> model.getRepresentationEmbed(true, true),
                null);
    }
}
