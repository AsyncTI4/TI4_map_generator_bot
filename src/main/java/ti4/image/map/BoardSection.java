package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.tile.ExtrasStepTiles;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;
import ti4.model.MapPositionMapper;
import ti4.service.map.FractureService;

final class BoardSection {

    private static final Color OUT_OF_FOCUS_SHADE = new Color(0, 0, 0, 165);

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
        this.tileLayers = ctx.fog().tileLayers(ctx.displayType());
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

    static List<Tile> shownTiles(RenderContext ctx) {
        return ctx.fog().tiles().entrySet().stream()
                .filter(entry -> entry.getKey() != null && ctx.board().isInShownRegion(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private void drawTiles(Map<String, Tile> tilesToDisplay) {
        if (!ctx.drawsBoard()) {
            return;
        }
        Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
        tileMap.keySet().removeIf(position -> !board.isInShownRegion(position));
        if (game.isShowMapSetup() || tilesToDisplay.isEmpty()) {
            drawSetupTiles(tileMap);
        }

        tileMap.remove(null);
        List<String> sortedTiles = tileMap.keySet().stream().sorted().toList();
        Set<String> tilesWithExtra = ExtrasStepTiles.positions(game);

        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Tile));
        GhostHexes.draw(ctx, g2, tileMap.keySet());
        tilesWithExtra.forEach(key -> addTile(tileMap.get(key), TileStep.Extras));
        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Units));
        if (!game.getTileDistances().isEmpty()) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Distance));
        }
        TileStep displayTypeStep = displayTypeStep(ctx.displayType());
        if (displayTypeStep != null) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), displayTypeStep));
        }
        dimOutOfFocus(sortedTiles);
        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.TileNumber));
    }

    private void dimOutOfFocus(List<String> positions) {
        MapFocus focus = ctx.focus();
        if (focus == null) {
            return;
        }
        Color previousColor = g2.getColor();
        g2.setColor(OUT_OF_FOCUS_SHADE);
        for (String position : positions) {
            Rectangle hex = focus.isDimmed(position) ? board.hexBounds(position) : null;
            if (hex != null) {
                g2.fillPolygon(MapFrame.hexPolygon(hex));
            }
        }
        g2.setColor(previousColor);
    }

    private void drawSetupTiles(Map<String, Tile> tileMap) {
        boolean showFracture = FractureService.isFractureRegionOnMap(game);
        int ringCount = Math.clamp(game.getRingCount(), BoardLayout.RING_MIN_COUNT, BoardLayout.RING_MAX_COUNT);
        for (String position : MapPositionMapper.getTilePositions()) {
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
    static TileStep displayTypeStep(DisplayType displayType) {
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
            Point positionPoint = MapPositionMapper.getTilePosition(position);
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
