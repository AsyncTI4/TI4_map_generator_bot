package ti4.commands.explore;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class ExploreCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new ExploreLookAtTop())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.EXPLORE;
    }

    public String getDescription() {
        return "Explore";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
