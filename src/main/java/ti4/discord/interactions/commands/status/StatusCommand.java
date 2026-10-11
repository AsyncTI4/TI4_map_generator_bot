package ti4.discord.interactions.commands.status;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class StatusCommand extends AbstractParentCommand {

    public StatusCommand() {
        super(
                Constants.STATUS,
                "Status Phase",
                new Cleanup(),
                new PersonalCleanup(),
                new RevealStage1(),
                new RevealStage2(),
                new ShufflePublicBack(),
                new UnrevealPublic(),
                new ScorePublic(),
                new PeekPublicObjectiveDeck(),
                new UnscorePublic(),
                new AddCustomPO(),
                new RemoveCustomPO(),
                new SCTradeGoods(),
                new ListTurnOrder(),
                new ListTurnStats(),
                new ListDiceLuck(),
                new ListSpends(),
                new MarkFollowed(),
                new POInfo(),
                new ShufflePublicDeck());
    }
}
