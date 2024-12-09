package ti4.commands2.installation;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class InstallationCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
        new AddSweepToken(),
        new RemoveSweepToken()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.INSTALLATION;
    }

    @Override
    public String getDescription() {
        return "Installations";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
