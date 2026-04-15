package ti4.discord.interactions.commands.tf;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
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
                    new ReverseSplice(),
                    new StartNewSplice(),
                    new DoEdictPhase(),
                    new SetupStartingFleet(),
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
