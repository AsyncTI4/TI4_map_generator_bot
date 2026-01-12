package ti4.commands.ai;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;

/**
 * Parent command for AI player management.
 * Provides subcommands for joining, leaving, configuring, and monitoring AI players.
 */
public class AiCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new AiJoin(),
                    new AiLeave(),
                    new AiDifficulty(),
                    new AiPause(),
                    new AiResume(),
                    new AiStatus())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return "ai";
    }

    @Override
    public String getDescription() {
        return "AI Player Management";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
