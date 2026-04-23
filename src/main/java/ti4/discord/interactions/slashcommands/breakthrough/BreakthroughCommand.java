package ti4.discord.interactions.slashcommands.breakthrough;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;

public class BreakthroughCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new BreakthroughActivate(),
                    new BreakthroughUnlock(),
                    new BreakthroughLock(),
                    new BreakthroughReady(),
                    new BreakthroughExhaust(),
                    new BreakthroughInfo(),
                    new BreakthroughSetTg())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.BREAKTHROUGH;
    }

    @Override
    public String getDescription() {
        return "Breakthrough handling";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
