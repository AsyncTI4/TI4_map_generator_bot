package ti4.discord.interactions.commands;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractParentCommand implements ParentCommand {

    private final String name;
    private final String description;
    private final Map<String, Subcommand> subcommands;

    protected AbstractParentCommand(String name, String description, Subcommand... subcommands) {
        this.name = name;
        this.description = description;
        this.subcommands =
                Stream.of(subcommands).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
