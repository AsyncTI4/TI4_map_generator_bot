package ti4.discord.interactions.commands;

import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.jetbrains.annotations.NotNull;

public abstract class SubcommandGroup extends SubcommandGroupData {

    protected SubcommandGroup(@NotNull String name, @NotNull String description) {
        super(name, description);

        Map<String, Subcommand> subcommands = getGroupSubcommands();
        if (subcommands == null) {
            throw new IllegalStateException("Subcommand group " + name + " returned null subcommands map");
        }
        addSubcommands(subcommands.values());
    }

    public abstract Map<String, Subcommand> getGroupSubcommands();
}
