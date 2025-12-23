package ti4.commands.spin;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SpinCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new ListSpinSettings(), new AddSpinSetting(), new ExecuteSpin(), new RemoveSpinSetting())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.SPIN;
    }

    public String getDescription() {
        return "Spin tiles in rings";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
