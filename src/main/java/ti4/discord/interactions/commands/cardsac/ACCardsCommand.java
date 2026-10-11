package ti4.discord.interactions.commands.cardsac;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class ACCardsCommand extends AbstractParentCommand {

    public ACCardsCommand() {
        super(
                Constants.CARDS_AC,
                "Action Cards",
                new ACInfo(),
                new DrawAC(),
                new DiscardAC(),
                new PurgeAC(),
                new DiscardACRandom(),
                new ShowAC(),
                new ShowACToAll(),
                new PlayAC(),
                new ShuffleACDeck(),
                new ShowAllAC(),
                new ShowACRemainingCardCount(),
                new ShowAllUnplayedACs(),
                new PickACFromDiscard(),
                new PickACFromPurged(),
                new ShowDiscardActionCards(),
                new ShowPurgedActionCards(),
                new ShowGarboziaActionCards(),
                new ShuffleACBackIntoDeck(),
                new ShuffleACFromHand(),
                new RevealAndPutACIntoDiscard(),
                new SendAC(),
                new SendACRandom(),
                new DrawSpecificAC(),
                new MakeCopiesOfACs());
    }
}
