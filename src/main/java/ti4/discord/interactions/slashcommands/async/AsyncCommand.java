package ti4.discord.interactions.slashcommands.async;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;

public class AsyncCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(new ShowHeroes(), new ShowTourneyWinners())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return "async";
    }

    @Override
    public String getDescription() {
        return "Random Async fun stuff, like Twilight Imperium Global League (TIGL) and Tournaments";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
