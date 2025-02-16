package ti4.service.planet;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class FlipTileService {

    @NotNull
    public static Tile flipTileIfNeeded(Tile tile, Game game) {
        if ("82a".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);

            String planetTileName = AliasHandler.resolveTile("82b");
            if (!PositionMapper.isTilePositionValid(position)) {
                throw new IllegalStateException("Position tile not allowed");
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                throw new IllegalStateException("Could not find tile: " + planetTileName);
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        } else if ("82ah".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);

            String planetTileName = AliasHandler.resolveTile("82bh");
            if (!PositionMapper.isTilePositionValid(position)) {
                throw new IllegalStateException("Position tile not allowed");
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                throw new IllegalStateException("Could not find tile: " + planetTileName);
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        }
        return tile;
    }

    public static Tile flipTileIfNeeded(ButtonInteractionEvent event, Tile tile, Game game) {
        if ("82a".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);
            String planetTileName = AliasHandler.resolveTile("82b");
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        } else if ("82ah".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);
            String planetTileName = AliasHandler.resolveTile("82bh");
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        }
        return tile;
    }
}
