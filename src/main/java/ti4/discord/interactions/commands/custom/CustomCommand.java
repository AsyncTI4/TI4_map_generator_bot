package ti4.discord.interactions.commands.custom;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class CustomCommand extends AbstractParentCommand {

    public CustomCommand() {
        super(
                Constants.CUSTOM,
                "Custom",
                RemoveCardFromGame.secretObjective(),
                new SoAddToGame(),
                RemoveCardFromGame.agenda(),
                RemoveCardFromGame.actionCard(),
                RemoveCardFromGame.relic(),
                new SCAddToGame(),
                new SCRemoveFromGame(),
                RemoveCardFromGame.publicObjective(),
                new PoSetDeck(),
                RemoveCardFromGame.discardAgenda(),
                new SetThreadName(),
                new PeekAtSecretDeck(),
                new PeekAtStage(1),
                new PeekAtStage(2),
                new SetUpPeakableObjectives(),
                new SwapStage(1),
                new ShuffleBackInUnrevealedObj(),
                new SwapStage(2),
                new RevealSpecificStage(1),
                new RevealSpecificStage(2),
                new SpinTilesInRings(),
                new OfferAutoPassOptions(),
                new ChangeToBaseGame(),
                new CustomizationOptions());
    }
}
