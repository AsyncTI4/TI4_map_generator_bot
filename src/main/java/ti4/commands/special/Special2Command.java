package ti4.commands.special;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class Special2Command implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new SetupNeutralPlayer(),
            new GeneratePainBoxMapString()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.SPECIAL + "2";
    }

    @Override
    public String getDescription() {
        return "More special commands";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
