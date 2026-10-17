package ti4.ai.discord;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;

public class AiCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new AiAdd(),
                    new AiStatus(),
                    new AiPause(),
                    new AiResume(),
                    new AiDelegate(),
                    new AiRemove(),
                    new AiWatch())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return "ai";
    }

    @Override
    public String getDescription() {
        return "AI players: add them to a game, manage them, or watch them play each other";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
