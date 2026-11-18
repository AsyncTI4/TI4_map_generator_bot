package ti4.discord.interactions.commands.fow;

import java.util.Map;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.SubcommandGroup;
import ti4.helpers.Constants;

class FowPingGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = FOWCommand.inOrder(Stream.of(
            new PingActivePlayer(),
            new PingSystem(),
            new TargetPlanetPing(),
            new TargetSystemPing(),
            new TargetPlayerPing()));

    FowPingGroup() {
        super(Constants.PING, "Ping the active player, or the players who can see a system, planet or target");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }
}
