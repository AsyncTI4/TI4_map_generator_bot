package ti4.discord.interactions.commands.explore;

import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.model.ExploreModel;

class ExploreShuffleIntoDeckFromHand extends ExploreCardIdsSubcommand {

    ExploreShuffleIntoDeckFromHand() {
        super(Constants.SHUFFLE_INTO_DECK_FROM_HAND, "Discard an Exploration Card from the hand to deck.");
    }

    @Override
    protected String handleCard(Game game, String id, ExploreModel explore) {
        if (explore == null) return notFound(id);
        getPlayer().removeFragment(id);
        game.addExplore(id);
        return "Fragment discarded: " + explore.textRepresentation();
    }
}
