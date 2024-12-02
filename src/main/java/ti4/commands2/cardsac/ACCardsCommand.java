package ti4.commands2.cardsac;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class ACCardsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new ShuffleACBackIntoDeck(),
                    new RevealAndPutACIntoDiscard(),
                    new SendAC(),
                    new SendACRandom(),
                    new DrawSpecificAC(),
                    new MakeCopiesOfACs())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.CARDS_AC;
    }

    @Override
    public String getDescription() {
        return "Action Cards";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
