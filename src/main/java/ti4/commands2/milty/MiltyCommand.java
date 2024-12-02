package ti4.commands2.milty;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class MiltyCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new DebugMilty(),
            new ForcePick(),
            new SetupMilty(),
            new StartMilty(),
            new ShowMilty()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.MILTY;
    }

    @Override
    public String getDescription() {
        return "Milty draft";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
