package ti4.discord.interactions.commands.homebrew.refracture;

import java.util.Map;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;

public class RefractureGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> SUBCOMMANDS = Map.of("spawn_layout", new SpawnRefractureLayout());

    public RefractureGroup() {
        super("refracture", "Refracture commands");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return SUBCOMMANDS;
    }
}
