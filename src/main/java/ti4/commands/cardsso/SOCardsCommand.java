package ti4.commands.cardsso;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SOCardsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new ListAllScored())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.CARDS_SO;
    }

    @Override
    public String getDescription() {
        return "Secret Objectives";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
