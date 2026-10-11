package ti4.discord.interactions.commands.explore;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class ExploreCommand extends AbstractParentCommand {

    public ExploreCommand() {
        super(
                Constants.EXPLORE,
                "Explore",
                new ExploreDiscardFromDeck(),
                new ExploreShuffleIntoDeckFromHand(),
                new ExploreDrawAndDiscard(),
                new ExploreRemoveFromGame(),
                new ExploreShuffleBackIntoDeck(),
                new ExploreInfo(),
                new ExplorePlanet(),
                new ExploreReset(),
                new ExploreFrontier(),
                new ExploreUse(),
                new ExploreShuffle(),
                new ExploreLookAtTop(),
                new ExploreDiscardFragment());
    }
}
