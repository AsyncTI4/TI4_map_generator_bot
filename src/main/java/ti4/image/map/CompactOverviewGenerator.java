package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.image.io.ImageUploads;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.image.tile.UnexploredBridges;
import ti4.logging.BotLogger;
import ti4.service.fow.FogMemoryService;
import ti4.service.fow.FogViewer;

public final class CompactOverviewGenerator {

    static final int MAX_SIZE = 4000;
    private static final String CORNERS_TITLE = "corners";
    private static final String PART_SEPARATOR = " - ";
    private static final List<String> CORNERS = List.of("tl", "tr", "bl", "br");
    private static final List<TileStep> STEPS =
            List.of(TileStep.Tile, TileStep.Extras, TileStep.Units, TileStep.TileNumber);

    record Panel(String title, Map<String, Point> centres, int colorIndex) {

        Panel {
            centres = Collections.unmodifiableMap(new LinkedHashMap<>(centres));
        }
    }

    record Placed(Panel panel, Map<String, Point> centres, Rectangle label) {

        Color color() {
            return OverviewStyle.color(panel.colorIndex());
        }
    }

    record Layout(List<Placed> placed, HexIslandStyle.Canvas canvas) {

        int canvasWidth() {
            return canvas.width();
        }

        int canvasHeight() {
            return canvas.height();
        }
    }

    private CompactOverviewGenerator() {}

    public static boolean drawsOverlay(DisplayType displayType) {
        return BoardSection.displayTypeStep(displayType) != null;
    }

    public static FileUpload gmOverview(Game game) {
        return gmOverview(game, null);
    }

    public static FileUpload gmOverview(Game game, @Nullable DisplayType overlay) {
        return ImageUploads.createFileUpload(gmImage(game, overlay), game.getName() + "_overview", "webp");
    }

    public static FileUpload playerOverview(Game game, Player player) {
        return playerOverview(game, player, null);
    }

    public static FileUpload playerOverview(Game game, Player player, @Nullable DisplayType overlay) {
        return ImageUploads.createFileUpload(
                playerImage(game, player, overlay), game.getName() + "_my_overview", "webp");
    }

    public static BufferedImage gmImage(Game game) {
        return gmImage(game, null);
    }

    public static BufferedImage gmImage(Game game, @Nullable DisplayType overlay) {
        return render(
                game,
                gmPanels(game),
                new HashMap<>(game.getTileMap()),
                TileLayers.of(game, null, displayTypeFor(overlay)),
                withOverlay(withDistances(game, STEPS), overlay),
                header(game, "GM overview", overlay));
    }

    public static BufferedImage playerImage(Game game, Player player) {
        return playerImage(game, player, null);
    }

    public static BufferedImage playerImage(Game game, Player player, @Nullable DisplayType overlay) {
        FogMemoryService.refreshForPlayerOverview(game, player);
        TileLayers layers =
                TileLayers.forPreparedViewer(game, new FogViewer(game, true, player), displayTypeFor(overlay));
        Map<String, Tile> tiles = knownTiles(game, player, layers);
        return render(
                game,
                playerPanels(game, player, tiles.keySet()),
                tiles,
                layers,
                withOverlay(withDistances(game, STEPS), overlay),
                header(game, player.getFaction() + " overview", overlay));
    }

    private static DisplayType displayTypeFor(@Nullable DisplayType overlay) {
        return overlay == null ? DisplayType.map : overlay;
    }

    private static List<TileStep> withDistances(Game game, List<TileStep> steps) {
        if (game.getTileDistances().isEmpty()) {
            return steps;
        }
        List<TileStep> withDistances = new ArrayList<>(steps);
        withDistances.add(withDistances.indexOf(TileStep.TileNumber), TileStep.Distance);
        return withDistances;
    }

    private static List<TileStep> withOverlay(List<TileStep> steps, @Nullable DisplayType overlay) {
        TileStep overlayStep = overlay == null ? null : BoardSection.displayTypeStep(overlay);
        if (overlayStep == null) {
            return steps;
        }
        List<TileStep> withOverlay = new ArrayList<>(steps);
        withOverlay.add(withOverlay.indexOf(TileStep.TileNumber), overlayStep);
        return withOverlay;
    }

    private static String header(Game game, String view, @Nullable DisplayType overlay) {
        String header = game.getName() + " - " + view;
        if (overlay == null) {
            return header;
        }
        return header + " - " + StringUtils.capitalize(overlay.getValue().replace('_', ' '));
    }

    static List<Panel> gmPanels(Game game) {
        return splitIntoTouchingParts(
                panels(game, MapSegment.all(game), true, game.getTileMap().keySet()), UnaryOperator.identity());
    }

    static List<Panel> playerPanels(Game game, Player player, Set<String> known) {
        return splitIntoTouchingParts(
                panels(game, MapSegment.visibleTo(game, player), MapSegment.mainMapVisibleTo(game, player), known),
                CompactOverviewGenerator::centredOnItself);
    }

    private static List<Panel> splitIntoTouchingParts(
            List<Panel> panels, UnaryOperator<Map<String, Point>> partPlacement) {
        List<Panel> islands = new ArrayList<>();
        for (Panel panel : panels) {
            List<Map<String, Point>> parts = touchingParts(panel.centres());
            for (int index = 0; index < parts.size(); index++) {
                String title = parts.size() == 1 ? panel.title() : panel.title() + PART_SEPARATOR + (index + 1);
                islands.add(new Panel(title, partPlacement.apply(parts.get(index)), panel.colorIndex()));
            }
        }
        return islands;
    }

    static Map<String, Tile> knownTiles(Game game, Player player, TileLayers layers) {
        Set<String> visible = FoWHelper.visibleSystems(game, player);
        UnexploredBridges unexploredBridges = new UnexploredBridges(layers);
        Map<String, Tile> tiles = new HashMap<>();
        game.getTileMap().forEach((position, tile) -> {
            if (visible.contains(position)) {
                tiles.put(position, tile);
                return;
            }
            Tile remembered = unexploredBridges.fogTile(player, position);
            if (remembered != null && (!isUnknownBlank(remembered, player) || unexploredBridges.shows(remembered))) {
                tiles.put(position, remembered);
            }
        });
        return tiles;
    }

    private static boolean isUnknownBlank(Tile tile, Player player) {
        return UnexploredBridges.UNEXPLORED_TILE_ID.equals(tile.getTileID())
                && StringUtils.isBlank(tile.getFogLabel(player));
    }

    private static List<Panel> panels(Game game, List<MapSegment> segments, boolean withMain, Set<String> allowed) {
        int fractureYbump = BoardLayout.fractureYbump(game);
        List<Panel> panels = new ArrayList<>();
        if (withMain) {
            addPanel(
                    panels,
                    game,
                    MapSegment.mainDisplayName(game),
                    MapSegment.uncoveredMainPositions(game),
                    allowed,
                    fractureYbump);
        }
        for (MapSegment segment : segments) {
            addPanel(panels, game, segment.displayName(game), segment.positions(), allowed, fractureYbump);
        }
        Map<String, Point> corners = new LinkedHashMap<>();
        for (String corner : CORNERS) {
            if (allowed.contains(corner) && game.getTileByPosition(corner) != null) {
                int index = corners.size();
                corners.put(
                        corner, new Point(index * FocusIslands.COLUMN_STEP, (index % 2) * FocusIslands.HALF_ROW_STEP));
            }
        }
        if (!corners.isEmpty()) {
            panels.add(new Panel(CORNERS_TITLE, corners, panels.size()));
        }
        return panels;
    }

    private static void addPanel(
            List<Panel> panels,
            Game game,
            String title,
            Set<String> positions,
            Set<String> allowed,
            int fractureYbump) {
        Map<String, Point> centres = new LinkedHashMap<>();
        Point origin = null;
        for (String position : new TreeSet<>(positions)) {
            if (!allowed.contains(position) || BoardLayout.CORNER_POSITIONS.contains(position.toLowerCase())) {
                continue;
            }
            Rectangle hex = MapFrame.hexBounds(game, position, fractureYbump, 0, 0);
            if (hex == null) {
                continue;
            }
            Point centre = new Point((int) hex.getCenterX(), (int) hex.getCenterY());
            if (origin == null) {
                origin = centre;
            }
            centres.put(position, onLattice(centre, origin));
        }
        if (!centres.isEmpty()) {
            panels.add(new Panel(title, centres, panels.size()));
        }
    }

    static Point onLattice(Point centre, Point origin) {
        int column = Math.round((centre.x - origin.x) / (float) FocusIslands.COLUMN_STEP);
        int row = Math.round((centre.y - origin.y) / (float) FocusIslands.HALF_ROW_STEP);
        if ((column + row) % 2 != 0) {
            row++;
        }
        return new Point(origin.x + column * FocusIslands.COLUMN_STEP, origin.y + row * FocusIslands.HALF_ROW_STEP);
    }

    static List<Map<String, Point>> touchingParts(Map<String, Point> centres) {
        Map<Point, String> positionAt = new HashMap<>();
        centres.forEach((position, centre) -> positionAt.put(centre, position));
        Set<String> seen = new HashSet<>();
        List<Map<String, Point>> parts = new ArrayList<>();
        for (String start : centres.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            Map<String, Point> part = new TreeMap<>(MapFrame.POSITION_ORDER);
            ArrayDeque<String> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                String position = queue.poll();
                Point centre = centres.get(position);
                part.put(position, centre);
                for (Point step : HexPacker.NEIGHBOUR_OFFSETS) {
                    String next = positionAt.get(new Point(centre.x + step.x, centre.y + step.y));
                    if (next != null && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            parts.add(part);
        }
        parts.sort(Comparator.comparingInt((Map<String, Point> part) -> -part.size())
                .thenComparing(part -> part.keySet().iterator().next(), MapFrame.POSITION_ORDER));
        return parts;
    }

    private static Map<String, Point> centredOnItself(Map<String, Point> centres) {
        long sumX = 0;
        long sumY = 0;
        for (Point centre : centres.values()) {
            sumX += centre.x;
            sumY += centre.y;
        }
        int count = Math.max(1, centres.size());
        int shiftX = (int) (sumX / count);
        int shiftY = (int) (sumY / count);
        Map<String, Point> centred = new LinkedHashMap<>();
        centres.forEach((position, centre) -> centred.put(position, new Point(centre.x - shiftX, centre.y - shiftY)));
        return centred;
    }

    static Layout layout(List<Panel> panels, String header) {
        List<Panel> bySize = new ArrayList<>(panels);
        bySize.sort(Comparator.comparingInt((Panel panel) -> -panel.centres().size())
                .thenComparing(Panel::title));
        List<HexPacker.Shape> shapes = bySize.stream()
                .map(panel -> new HexPacker.Shape(
                        List.copyOf(panel.centres().values()),
                        HexIslandStyle.labelChoices(
                                panel.title(), panel.centres().values())))
                .toList();
        List<HexPacker.Placement> placements = HexPacker.pack(shapes);
        List<Placed> placed = new ArrayList<>();
        for (int index = 0; index < bySize.size(); index++) {
            Point offset = placements.get(index).offset();
            Map<String, Point> centres = new LinkedHashMap<>();
            bySize.get(index)
                    .centres()
                    .forEach((position, local) ->
                            centres.put(position, new Point(local.x + offset.x, local.y + offset.y)));
            placed.add(
                    new Placed(bySize.get(index), centres, placements.get(index).label()));
        }
        Rectangle content = HexIslandStyle.contentBounds(
                placed.stream().flatMap(box -> box.centres().values().stream()).toList(),
                placed.stream().map(Placed::label).toList());
        return new Layout(placed, HexIslandStyle.Canvas.of(content, MAX_SIZE, header));
    }

    private static BufferedImage render(
            Game game,
            List<Panel> panels,
            Map<String, Tile> tiles,
            TileLayers tileLayers,
            List<TileStep> steps,
            String header) {
        Layout layout = layout(panels, header);
        BufferedImage image = layout.canvas().newImage();
        Graphics2D graphics = OverviewStyle.smoothGraphics(image);
        try {
            OverviewStyle.drawHeader(graphics, header);
            layout.canvas().enter(graphics);
            drawTiles(game, graphics, layout, tiles, tileLayers, steps);
            layout.placed()
                    .forEach(box ->
                            HexIslandStyle.drawOutline(graphics, box.centres().values(), box.color()));
            layout.placed()
                    .forEach(box -> HexIslandStyle.drawLabel(
                            graphics, box.label(), box.panel().title(), box.color()));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawTiles(
            Game game,
            Graphics2D graphics,
            Layout layout,
            Map<String, Tile> tiles,
            TileLayers tileLayers,
            List<TileStep> steps) {
        for (TileStep step : steps) {
            for (Placed box : layout.placed()) {
                box.centres().forEach((position, centre) -> {
                    Tile tile = tiles.get(position);
                    if (tile == null) {
                        return;
                    }
                    try {
                        HexIslandStyle.drawLayer(graphics, tileLayers.draw(tile, step), tile, centre, step);
                    } catch (Exception e) {
                        BotLogger.error(
                                "Compact overview could not draw tile " + tile.getTileID() + " at " + position + " in "
                                        + game.getName(),
                                e);
                    }
                });
            }
        }
    }
}
