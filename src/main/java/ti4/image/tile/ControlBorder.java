package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.draw.HexOverlays;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

final class ControlBorder {

    private ControlBorder() {}

    static void draw(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        boolean unlocked = scene.displayType() == DisplayType.unlocked;
        Player controllingPlayer = game.getPlayerThatControlsTile(tile);
        if (("off".equals(game.getHexBorderStyle()) && !unlocked) || controllingPlayer == null || layer.isSpiral()) {
            return;
        }
        List<Integer> openSides = sidesSharedWith(game, tile, controllingPlayer);
        if (scene.isPrivateView() && scene.fowPlayer() == null) openSides.clear();
        String hexBorderStyle = unlocked ? "solid" : game.getHexBorderStyle();
        BufferedImage border =
                HexOverlays.border(hexBorderStyle, Mapper.getColor(controllingPlayer.getColor()), openSides);
        layer.g().drawImage(border, TILE_PADDING, TILE_PADDING, null);
    }

    private static List<Integer> sidesSharedWith(Game game, Tile tile, Player controllingPlayer) {
        int sideNum = 0;
        List<Integer> openSides = new ArrayList<>();
        for (String adj : MapPositionMapper.getAdjacentTilePositions(tile.getPosition())) {
            if (game.getPlayerThatControlsTile(adj) == controllingPlayer) {
                openSides.add(sideNum);
            }
            sideNum++;
        }
        return openSides;
    }
}
