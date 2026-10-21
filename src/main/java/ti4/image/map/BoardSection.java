package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;
import ti4.model.BorderAnomalyHolder;
import ti4.model.PositionMapper;
import ti4.service.map.FractureService;

final class BoardSection {

    private final RenderContext ctx;
    private final Graphics2D g2;
    private final Game game;
    private final BoardLayout board;
    private final TileLayers tileLayers;

    private BoardSection(RenderContext ctx, Graphics2D g2) {
        this.ctx = ctx;
        this.g2 = g2;
        this.game = ctx.game();
        this.board = ctx.board();
        this.tileLayers = TileLayers.forPreparedViewer(game, ctx.fog().fogViewer(), ctx.displayType());
    }

    static void draw(RenderContext ctx, Graphics2D g2) {
        new BoardSection(ctx, g2).drawInsideFrame();
    }

    private void drawInsideFrame() {
        Shape previousClip = g2.getClip();
        if (board.mapFrame() != null) {
            g2.setClip(0, 0, ctx.canvas().width(), board.mapFrame().height());
        }
        drawTiles(ctx.fog().tiles());
        g2.setClip(previousClip);
    }

    private void drawTiles(Map<String, Tile> tilesToDisplay) {
        DisplayType basicType = ctx.basicType();
        if (basicType != DisplayType.all && basicType != DisplayType.map) {
            return;
        }
        Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
        tileMap.keySet().removeIf(position -> !board.isInShownRegion(position));
        if (game.isShowMapSetup() || tilesToDisplay.isEmpty()) {
            drawSetupTiles(tileMap);
        }

        tileMap.remove(null);
        List<String> sortedTiles = tileMap.keySet().stream().sorted().toList();
        Set<String> tilesWithExtra =
                new HashSet<>(game.getAdjacentTileOverrides().values());
        tilesWithExtra.addAll(game.getBorderAnomalies().stream()
                .map(BorderAnomalyHolder::getTile)
                .collect(Collectors.toSet()));

        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Tile));
        GhostHexes.draw(ctx, g2, tileMap.keySet());
        tilesWithExtra.forEach(key -> addTile(tileMap.get(key), TileStep.Extras));
        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Units));
        if (!game.getTileDistances().isEmpty()) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Distance));
            game.setTileDistances(new HashMap<>());
        }
        TileStep displayTypeStep = displayTypeStep(ctx.displayType());
        if (displayTypeStep != null) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), displayTypeStep));
        }
        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.TileNumber));
    }

    private void drawSetupTiles(Map<String, Tile> tileMap) {
        boolean showFracture = FractureService.isFractureRegionOnMap(game);
        int ringCount = Math.clamp(game.getRingCount(), BoardLayout.RING_MIN_COUNT, BoardLayout.RING_MAX_COUNT);
        for (String position : PositionMapper.getTilePositions()) {
            String tileRing = "0";
            if (position.startsWith("frac") && !showFracture) continue;
            if (position.length() == 3) {
                tileRing = position.substring(0, 1);
            } else if (position.length() == 4) {
                tileRing = position.substring(0, 2);
            }
            int tileRingNumber = -1;
            try {
                tileRingNumber = Integer.parseInt(tileRing);
            } catch (Exception e) {
                BotLogger.error("Hitting an error", e);
            }

            Tile setupTile = null;
            if (tileRingNumber > -1 && tileRingNumber <= ringCount && !tileMap.containsKey(position)) {
                setupTile = new Tile("0gray", position);
            } else if (tileRingNumber > -1 && tileRingNumber <= ringCount + 1 && !tileMap.containsKey(position)) {
                setupTile = new Tile("0border", position);
            }
            if (setupTile != null) {
                addTile(setupTile, TileStep.Tile);
                addTile(setupTile, TileStep.TileNumber);
            }
        }
    }

    @Nullable
    private static TileStep displayTypeStep(DisplayType displayType) {
        return switch (displayType) {
            case wormholes -> TileStep.Wormholes;
            case anomalies -> TileStep.Anomalies;
            case aetherstream -> TileStep.Aetherstream;
            case exile -> TileStep.Exile;
            case legendaries -> TileStep.Legendaries;
            case empties -> TileStep.Empties;
            case spacecannon -> TileStep.SpaceCannon;
            case traits -> TileStep.Traits;
            case techskips -> TileStep.TechSkips;
            case attachments -> TileStep.Attachments;
            default -> null;
        };
    }

    private void addTile(Tile tile, TileStep step) {
        if (tile == null || tile.getTileID() == null) {
            return;
        }
        try {
            String position = tile.getPosition();
            Point positionPoint = PositionMapper.getTilePosition(position);
            if (positionPoint == null) {
                if ("-1".equalsIgnoreCase(tile.getTileID())) {
                    return;
                }
                throw new Exception("Could not map tile to a position on the map: " + game.getName());
            }

            Point origin = board.tileOrigin(position, positionPoint);
            if (origin == null) {
                return;
            }

            tileLayers.paint(tile, step, g2, origin.x, origin.y);
        } catch (Exception exception) {
            BotLogger.error(
                    "Tile Error, when building map `" + game.getName() + "`, tile: " + tile.getTileID(), exception);
        }
    }
}
