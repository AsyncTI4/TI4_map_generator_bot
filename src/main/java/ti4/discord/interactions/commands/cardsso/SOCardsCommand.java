package ti4.discord.interactions.commands.cardsso;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class SOCardsCommand extends AbstractParentCommand {

    public SOCardsCommand() {
        super(
                Constants.CARDS_SO,
                "Secret Objectives",
                new DrawSO(),
                new ShuffleSecretDeck(),
                new DiscardSO(),
                new SOInfo(),
                new ShowSO(),
                new ShowSOToAll(),
                new ScoreSO(),
                new DealSO(),
                new UnscoreSO(),
                new ShowAllSO(),
                new ShowAllSOToAll(),
                new ShowRandomSO(),
                new DealSOToAll(),
                new DrawSpecificSO(),
                new ShowUnScoredSOs(),
                new ListAllScored(),
                new SendSecretObjectiveCommand());
    }
}
