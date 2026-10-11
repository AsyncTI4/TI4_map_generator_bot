package ti4.discord.interactions.commands.explore;

import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.model.ExploreModel;

class ExploreRemoveFromGame extends ExploreCardIdsSubcommand {

    ExploreRemoveFromGame() {
        super(Constants.REMOVE, "Remove an Exploration card from the game.");
        markSuspicious();
    }

    @Override
    protected String handleCard(Game game, String id, ExploreModel explore) {
        game.purgeExplore(id);
        if (explore == null) return "Removed id without matching card: " + id;
        return "Exploration card removed: " + explore.textRepresentation();
    }
}
