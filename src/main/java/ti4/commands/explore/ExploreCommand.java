package ti4.commands.explore;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
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
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
                CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
