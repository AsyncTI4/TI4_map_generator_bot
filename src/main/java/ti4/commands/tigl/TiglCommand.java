package ti4.commands.tigl;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class TiglCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
        new ChangeNickname()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.TIGL;
    }

    @Override
    public String getDescription() {
        return "Twilight Imperium Global League";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
