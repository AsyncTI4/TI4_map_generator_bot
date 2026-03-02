package ti4.commands.rules;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;

public class RulesCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands =
            Stream.of(new AskRulesQuestion()).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return "rules";
    }

    @Override
    public String getDescription() {
        return "Rules related commands";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
