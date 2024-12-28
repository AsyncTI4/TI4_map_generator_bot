package ti4.commands2.fow;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class FOWCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new AddCustomAdjacentTile(),
                    new AddAdjacencyOverride(),
                    new AddAdjacencyOverrideList(),
                    new AddFogTile(),
                    new CheckChannels(),
                    new PingActivePlayer(),
                    new PingSystem(),
                    new RemoveAdjacencyOverride(),
                    new RemoveAllAdjacencyOverrides(),
                    new RemoveFogTile(),
                    new RemoveCustomAdjacentTile(),
                    new RemoveAllCustomAdjacentTiles(),
                    new SetFogFilter(),
                    new Whisper(),
                    new Announce(),
                    new FOWOptions(),
                    new ShowGameAsPlayer(),
                    new PrivateCommunicationsCheck())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


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
