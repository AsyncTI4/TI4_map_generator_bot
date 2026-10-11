package ti4.discord.interactions.commands.explore;

import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.model.ExploreModel;

class ExploreShuffleBackIntoDeck extends ExploreCardIdsSubcommand {

    ExploreShuffleBackIntoDeck() {
        super(
                Constants.SHUFFLE_BACK_INTO_DECK,
                "Shuffle an exploration card back into the deck, including purged cards",
                "Exploration card ID, which is found between ()");
        markSuspicious();
    }

    @Override
    protected String handleCard(Game game, String id, ExploreModel explore) {
        if (explore == null) return notFound(id);
        game.addExplore(id);
        return "Card shuffled into exploration deck: " + explore.textRepresentation();
    }
}
