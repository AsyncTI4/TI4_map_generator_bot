package ti4.image.tile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.FoWHelper;
import ti4.image.io.Fonts;
import ti4.image.io.ImageUploads;
import ti4.logging.BotLogger;
import ti4.model.BorderAnomalyHolder;
import ti4.model.PositionMapper;
import ti4.service.fow.FogViewer;

public final class SystemContextImage {

    private static final int RING_WIDTH = 260;
    private static final int MARGIN_X = 100;
    private static final int MARGIN_Y = 100;

    private final Game game;
    private final TileLayers layers;
    private final int rings;
    private final String focus;

    private SystemContextImage(Game game, TileLayers layers, int rings, String focus) {
        this.game = game;
        this.layers = layers;
        this.rings = rings;
        this.focus = focus;
    }

    public static FileUpload upload(
            Game game, @Nullable GenericInteractionCreateEvent event, int rings, String focusPosition) {
        return upload(game, event, rings, focusPosition, null);
    }

    public static FileUpload upload(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            int rings,
            String focusPosition,
            @Nullable Player explicitViewer) {
        return ImageUploads.createFileUpload(render(game, event, rings, focusPosition, explicitViewer), game.getName());
    }

    public static BufferedImage render(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            int rings,
            String focusPosition,
            @Nullable Player explicitViewer) {
        TileLayers layers = TileLayers.of(game, event, null, explicitViewer);
        return new SystemContextImage(game, layers, rings, focusPosition).draw();
    }

    private BufferedImage draw() {
        Map<String, Tile> tilesToDisplay = tilesInRange();
        int width = TileGeometry.TILE_WIDTH + (RING_WIDTH * 2 * rings) + MARGIN_X;
        int height = TileGeometry.TILE_HEIGHT * (2 * rings + 1) + MARGIN_Y;
        var mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = mainImage.createGraphics();

        try {
            Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
            tileMap.remove(null);

            Set<String> tiles = tileMap.keySet();
            Set<String> tilesWithExtra =
                    new HashSet<>(game.getAdjacentTileOverrides().values());
            tilesWithExtra.addAll(game.getBorderAnomalies().stream()
                    .map(BorderAnomalyHolder::getTile)
                    .collect(Collectors.toSet()));

            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Tile));
            tilesWithExtra.forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Extras));
            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Units));
            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.TileNumber));

            graphics.setFont(Fonts.ti(32));
            graphics.setColor(Color.WHITE);
            String timeStamp = DateTimeHelper.getFormattedTimestampWithMillis();
            graphics.drawString(game.getName() + " " + timeStamp, 0, 34);
        } catch (Exception e) {
            BotLogger.error(game.getName() + ": Could not save generated system info image", e);
        }

        return mainImage;
    }

    private Map<String, Tile> tilesInRange() {
        Map<String, Tile> tilesToDisplay = new HashMap<>(game.getTileMap());
        Set<String> keysToRemove = new HashSet<>(tilesToDisplay.keySet());
        keysToRemove.removeAll(positionsInRange());
        for (String position : keysToRemove) {
            tilesToDisplay.remove(position);
        }

        FogViewer viewer = layers.viewer();
        if (viewer.privateView()) {
            Player fowPlayer = viewer.player();
            Set<String> hidden = new HashSet<>(tilesToDisplay.keySet());
            hidden.removeAll(viewer.visibleSystems());
            for (String position : hidden) {
                tilesToDisplay.remove(position);
                if (fowPlayer != null) {
                    tilesToDisplay.put(position, fowPlayer.buildFogTile(position, fowPlayer));
                }
            }
        }
        return tilesToDisplay;
    }

    private Set<String> positionsInRange() {
        Set<String> tileSet = new HashSet<>(Collections.singleton(focus));
        Set<String> tilesToCheck = new HashSet<>(Collections.singleton(focus));
        for (int i = 0; i < rings; i++) {
            Set<String> nextTiles = new HashSet<>();
            for (String tile : tilesToCheck) {
                for (String adjacent : FoWHelper.traverseAdjacencies(game, true, tile)) {
                    if (tileSet.add(adjacent)) {
                        nextTiles.add(adjacent);
                    }
                }
            }
            tilesToCheck = nextTiles;
        }
        return tileSet;
    }

    private void addTile(Graphics2D graphics, Tile tile, TileStep step) {
        if (tile == null || tile.getTileID() == null) {
            return;
        }
        try {
            Point positionPoint = PositionMapper.getTilePosition(tile.getPosition());
            if (positionPoint == null) {
                throw new Exception("Could not map tile to a position on the map: " + game.getName());
            }

            Point focusPoint = PositionMapper.getTilePosition(focus);
            int offsetY =
                    focusPoint == null ? 0 : -1 * focusPoint.y + (MARGIN_Y / 2) + (rings * TileGeometry.TILE_HEIGHT);
            int offsetX = focusPoint == null ? 0 : -1 * focusPoint.x + (MARGIN_X / 2) + (rings * RING_WIDTH);
            int tileX = positionPoint.x + offsetX - TileGeometry.TILE_PADDING;
            int tileY = positionPoint.y + offsetY - TileGeometry.TILE_PADDING;

            layers.paint(tile, step, graphics, tileX, tileY);
        } catch (Exception exception) {
            BotLogger.error("Tile Error, when building map: " + tile.getTileID(), exception);
        }
    }
}
