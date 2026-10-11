package ti4.discord.interactions.commands.map;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class MapCommand extends AbstractParentCommand {

    public MapCommand() {
        super(
                Constants.MAP,
                "Game",
                new AddTile(),
                new AddTileList(),
                new RemoveTile(),
                new SpawnFracture(),
                new AddBorderAnomaly(),
                new RemoveBorderAnomaly(),
                new InteractiveBuilder(),
                new Preset(),
                new RotateHyperlane(),
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
                new CustomHyperlanes(),
                new ImportFromJson(),
                new ExportToJson());
    }
}
