package ti4.commands.custom;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class CustomCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new SoRemoveFromGame(),
            new SoAddToGame(),
            new AgendaRemoveFromGame(),
            new ACRemoveFromGame(),
            new SCAddToGame(),
            new SCRemoveFromGame(),
            new PoRemoveFromGame(),
            new DiscardSpecificAgenda(),
            new SetThreadName(),
            new PeekAtObjectiveDeck(),
            new PeekAtStage1(),
            new PeekAtStage2(),
            new SetUpPeakableObjectives(),
            new SwapStage1(),
            new ShuffleBackInUnrevealedObj(),
            new SwapStage2(),
            new RevealSpecificStage1(),
            new RevealSpecificStage2(),
            new SpinTilesInRings(),
            new OfferAutoPassOptions(),
            new OfferAFKTimeOptions(),
            new ChangeToBaseGame(),
            new CustomizationOptions());

    @Override
    public String getActionId() {
        return Constants.CUSTOM;
    }

    @Override
    public String getActionDescription() {
        return "Custom";
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
