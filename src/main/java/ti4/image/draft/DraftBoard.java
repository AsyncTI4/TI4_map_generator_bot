package ti4.image.draft;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;

public record DraftBoard(int extraX, int extraY) {

    private static final int RING_MAX_COUNT = 8;
    private static final int RING_MIN_COUNT = 3;
    private static final int TILE_PADDING = 100;
    private static final int SPACE_FOR_TILE_HEIGHT = 300;
    private static final int RING_WIDTH = 520;

    public static Map<String, Tile> tilesOnBoard(Game game) {
        Map<String, Tile> tiles = new HashMap<>(game.getTileMap());
        tiles.remove("tl");
        tiles.remove("tr");
        tiles.remove("bl");
        tiles.remove("br");
        tiles.entrySet().removeIf(e -> e.getValue() == null || e.getValue().getTileID() == null);
        tiles.entrySet()
                .removeIf(e -> MapPositionMapper.getTilePosition(e.getValue().getPosition()) == null);
        return tiles;
    }

    int width(Game game) {
        float ringCount = ringCount(game);
        ringCount += ringCount == RING_MIN_COUNT ? 1.5f : 1;
        return (int) (ringCount * RING_WIDTH + extraX * 2);
    }

    int height(Game game) {
        return (ringCount(game) + 1) * SPACE_FOR_TILE_HEIGHT * 2 + extraY * 2;
    }

    @Nullable
    Point tilePosition(Game game, String position) {
        Point raw = MapPositionMapper.getTilePosition(position);
        if (raw == null) return null;
        Point scaled = MapPositionMapper.getScaledTilePosition(game, position, raw.x, raw.y);
        return new Point(scaled.x + extraX - TILE_PADDING, scaled.y + extraY - TILE_PADDING);
    }

    static Point contentOrigin(Point tilePosition) {
        return new Point(tilePosition.x + TILE_PADDING, tilePosition.y + TILE_PADDING + 20);
    }

    private static int ringCount(Game game) {
        return Math.clamp(game.getRingCount(), RING_MIN_COUNT, RING_MAX_COUNT);
    }
}
