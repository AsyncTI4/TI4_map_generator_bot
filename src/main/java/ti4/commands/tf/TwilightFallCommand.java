package ti4.commands.tf;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class TwilightFallCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new DrawRandomAbility(),
                    new DrawRandomUnit(),
                    new DrawRandomGenome(),
                    new DrawSpecificParadigm(),
                    new DrawRandomParadigm(),
                    new RadicalAdvancement(),
                    new FixColors(),
                    new StartNewSplice(),
                    new DoEdictPhase(),
                    new DiscardVeiledCard())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.TF_COMMAND;
    }

    @Override
    public String getDescription() {
        return "Twilights Fall Commands";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
