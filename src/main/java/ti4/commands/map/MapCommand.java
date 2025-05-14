package ti4.commands.map;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class MapCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
        new AddTile(),
        new AddTileList(),
        new RemoveTile(),
        new AddBorderAnomaly(),
        new RemoveBorderAnomaly(),
        new InteractiveBuilder(),
        new Preset(),
        new ShowMapSetup(),
        new ShowMapString(),
        new SetMapTemplate(),
        new PreviewMapTemplate(),
        new MoveTile(),
        new AddTileRandom(),
        new AddTileListRandom(),
        new AddCustomAdjacentTile(),
        new AddAdjacencyOverride(),
        new AddAdjacencyOverrideList(),
        new RemoveAdjacencyOverride(),
        new RemoveCustomAdjacentTile(),
        new CustomHyperlanes()).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.MAP;
    }

    @Override
    public String getDescription() {
        return "Game";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
