package ti4.commands.fow;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class FOWCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
        new AddFogTile(),
        new RemoveFogTile(),
        new CheckChannels(),
        new PingActivePlayer(),
        new PingSystem(),
        new Whisper(),
        new Announce(),
        new FOWOptions(),
        new ShowGameAsPlayer(),
        new PrivateCommunicationsCheck(),
        new GMCommand(),
        new CreateFoWGameButton()).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.FOW;
    }

    public String getDescription() {
        return "Fog of War";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
