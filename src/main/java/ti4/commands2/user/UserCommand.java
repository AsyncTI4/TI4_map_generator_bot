package ti4.commands2.user;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class UserCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new ShowUserSettings(),
                    new SetPreferredColourList(),
                    new SetPersonalPingInterval())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.USER;
    }

    @Override
    public String getDescription() {
        return "User";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
