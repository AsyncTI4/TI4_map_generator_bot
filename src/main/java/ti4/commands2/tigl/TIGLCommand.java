package ti4.commands2.tigl;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;


public class TIGLCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new TIGLShowHeroes()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.TIGL;
    }

    @Override
    public String getDescription() {
        return "Twilight Imperium Global League (TIGL)";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
