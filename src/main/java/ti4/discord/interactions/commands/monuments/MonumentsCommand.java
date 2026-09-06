package ti4.discord.interactions.commands.monuments;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;

public class MonumentsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(new MonumentReady(), new MonumentExhaust())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return "monument";
    }

    @Override
    public String getDescription() {
        return "Monument";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
