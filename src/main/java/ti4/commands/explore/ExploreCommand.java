package ti4.commands.explore;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class ExploreCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
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
            new ExploreLookAtTop());

    @Override
    public String getActionId() {
        return Constants.EXPLORE;
    }

    public String getActionDescription() {
        return "Explore";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
