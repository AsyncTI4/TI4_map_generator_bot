package ti4.commands.custom;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class CustomCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new CustomizationOptions())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.CUSTOM;
    }

    @Override
    public String getDescription() {
        return "Custom";
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
