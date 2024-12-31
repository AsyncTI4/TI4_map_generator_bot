package ti4.commands.status;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class StatusCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new Cleanup(),
            new PersonalCleanup(),
            new RevealStage1(),
            new RevealStage2(),
            new ShufflePublicBack(),
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
            new POInfo()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.STATUS;
    }

    @Override
    public String getDescription() {
        return "Status phase";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
