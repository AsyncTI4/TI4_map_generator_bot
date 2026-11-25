package ti4.discord.interactions.commands.fow;

import java.util.Map;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;
import ti4.helpers.Constants;

class FowMapGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands =
            FOWCommand.inOrder(Stream.of(new MapSegmentCommand(), new MapOverviewCommand(), new GalaxyCommand()));

    FowMapGroup() {
        super(Constants.FOW_MAP_GROUP, "GM: map sectors, the whole-map overview and galaxy names");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }
}
