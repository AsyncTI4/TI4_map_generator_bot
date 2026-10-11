package ti4.discord.interactions.commands.explore;

import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.model.ExploreModel;

class ExploreDiscardFromDeck extends ExploreCardIdsSubcommand {

    ExploreDiscardFromDeck() {
        super(Constants.DISCARD_FROM_DECK, "Discard an Exploration Card from the deck.");
        markSuspicious();
    }

    @Override
    protected String handleCard(Game game, String id, ExploreModel explore) {
        if (explore == null) return notFound(id);
        game.discardExplore(id);
        return "Card discarded: " + explore.textRepresentation();
    }
}
