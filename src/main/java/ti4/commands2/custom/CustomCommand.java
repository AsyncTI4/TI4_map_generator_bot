package ti4.commands2.custom;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
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
                    new PoSetDeck(),
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
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
