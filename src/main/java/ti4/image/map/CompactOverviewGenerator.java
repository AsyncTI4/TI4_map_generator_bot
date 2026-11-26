package ti4.image.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import ti4.model.BoardPosition;
import ti4.service.fow.FogMemoryService;
import ti4.service.fow.FogViewer;
import ti4.settings.users.FogViewSettings;

public final class CompactOverviewGenerator {

    static final int MAX_SIZE = 4000;
    private static final String CORNERS_TITLE = "corners";
    private static final char CORNERS_LETTER = 'X';
    private static final char MAIN_LETTER = 'M';
    private static final List<String> CORNERS = List.of("tl", "tr", "bl", "br");
    private static final List<TileStep> STEPS =
            List.of(TileStep.Tile, TileStep.Extras, TileStep.Units, TileStep.TileNumber);
    private static final int BOX_GAP = HexPacker.GUTTER;
    private static final int GROUP_GUTTER = 3 * HexPacker.GUTTER;
    private static final int BOX_PADDING = 24;
    private static final int BOX_LABEL_GAP = 12;
    private static final int BOX_ARC = 40;
    private static final int BOX_STROKE = 8;

    public enum Style {
        HEXES,
        BOXES
    }

    public record Options(Style style, int reach, boolean codes) {

        public static final int UNLIMITED_REACH = 0;
        public static final int DEFAULT_REACH = FogViewSettings.DEFAULT_REACH;
        public static final int MAX_REACH = 4;
        public static final Options DEFAULT = new Options(Style.HEXES, DEFAULT_REACH, false);

        public Options {
            reach = reach <= UNLIMITED_REACH || reach == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : Math.min(reach, MAX_REACH);
        }

        public boolean unlimitedReach() {
            return reach == Integer.MAX_VALUE;
        }

        public static Options of(@Nullable Player viewer) {
            if (viewer == null) {
                return DEFAULT;
            }
            return new Options(
                    viewer.isFogOverviewBoxes() ? Style.BOXES : Style.HEXES,
                    viewer.getFogOverviewReach(),
                    viewer.isFogOverviewCodes());
        }
    }

    record Panel(String title, Map<String, Point> centres, int colorIndex, boolean wholeBoard) {

        Panel {
            centres = Collections.unmodifiableMap(new LinkedHashMap<>(centres));
        }
    }

    record Group(Panel panel, String code, List<Map<String, Point>> islands) {

        Group {
            islands = List.copyOf(islands);
        }

        String title() {
            return panel.title();
        }

        String label(Options options) {
            return options.codes() ? code : panel.title();
        }

        Color color() {
            return OverviewStyle.color(panel.colorIndex());
        }
    }

    record Placed(
            Group group,
            List<Map<String, Point>> islands,
            Rectangle label,
            @Nullable Rectangle frame) {

        Map<String, Point> centres() {
            Map<String, Point> centres = new LinkedHashMap<>();
            islands.forEach(centres::putAll);
            return centres;
        }

        Color color() {
            return group.color();
        }
    }

    record Layout(List<Placed> placed, @Nullable OverviewLegend legend, HexIslandStyle.Canvas canvas) {

        int canvasWidth() {
            return canvas.width();
        }

        int canvasHeight() {
            return canvas.height();
        }
    }

    private record Cluster(Group group, List<Map<String, Point>> islands, List<Point> footprint) {

        static Cluster of(Group group, int reach) {
            List<HexPacker.Shape> shapes = group.islands().stream()
                    .map(island -> islandShape(island, reach))
                    .toList();
            List<HexPacker.Placement> placements = HexPacker.pack(shapes);
            List<Map<String, Point>> islands = new ArrayList<>();
            List<Point> footprint = new ArrayList<>();
            for (int index = 0; index < shapes.size(); index++) {
                Point offset = placements.get(index).offset();
                islands.add(moved(group.islands().get(index), offset));
                shapes.get(index).footprint().forEach(local -> footprint.add(translated(local, offset)));
            }
            return new Cluster(group, islands, footprint);
        }

        List<Point> centres() {
            return islands.stream().flatMap(island -> island.values().stream()).toList();
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
        return gmOverview(game, overlay, Options.DEFAULT);
    }

    public static FileUpload gmOverview(Game game, @Nullable DisplayType overlay, Options options) {
        return ImageUploads.createFileUpload(gmImage(game, overlay, options), game.getName() + "_overview", "webp");
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
        return gmImage(game, overlay, Options.DEFAULT);
    }

    public static BufferedImage gmImage(Game game, @Nullable DisplayType overlay, Options options) {
        return render(
                game,
                gmGroups(game, options.reach()),
                new HashMap<>(game.getTileMap()),
                TileLayers.of(game, null, displayTypeFor(overlay)),
                withOverlay(withDistances(game, STEPS), overlay),
                header(game, "GM overview", overlay),
                options);
    }

    public static BufferedImage playerImage(Game game, Player player) {
        return playerImage(game, player, null);
    }

    public static BufferedImage playerImage(Game game, Player player, @Nullable DisplayType overlay) {
        FogMemoryService.refreshForPlayerOverview(game, player);
        TileLayers layers =
                TileLayers.forPreparedViewer(game, new FogViewer(game, true, player), displayTypeFor(overlay));
        Map<String, Tile> tiles = knownTiles(game, player, layers);
        Options options = Options.of(player);
        return render(
                game,
                playerGroups(game, player, tiles.keySet(), options.reach()),
                tiles,
                layers,
                withOverlay(withDistances(game, STEPS), overlay),
                header(game, player.getFaction() + " overview", overlay),
                options);
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

    static List<Group> gmGroups(Game game, int reach) {
        return groups(
                panels(game, MapSegment.all(game), true, game.getTileMap().keySet()), reach, UnaryOperator.identity());
    }

    static List<Group> playerGroups(Game game, Player player, Set<String> known, int reach) {
        return groups(
                panels(game, MapSegment.visibleTo(game, player), MapSegment.mainMapVisibleTo(game, player), known),
                reach,
                CompactOverviewGenerator::centredOnItself);
    }

    private static List<Group> groups(
            List<Panel> panels, int reach, UnaryOperator<Map<String, Point>> islandPlacement) {
        List<String> codes = codes(panels);
        List<Group> groups = new ArrayList<>();
        for (int index = 0; index < panels.size(); index++) {
            Panel panel = panels.get(index);
            List<Map<String, Point>> islands = HexReach.parts(panel.centres(), reach).stream()
                    .map(islandPlacement)
                    .toList();
            groups.add(new Group(panel, codes.get(index), islands));
        }
        return groups;
    }

    static List<String> codes(List<Panel> panels) {
        Map<Character, Integer> sectorsPerLetter = new HashMap<>();
        List<String> codes = new ArrayList<>();
        for (Panel panel : panels) {
            char letter = letterOf(panel);
            if (panel.wholeBoard()) {
                codes.add(String.valueOf(letter));
            } else {
                codes.add(letter + String.valueOf(sectorsPerLetter.merge(letter, 1, Integer::sum)));
            }
        }
        return codes;
    }

    private static char letterOf(Panel panel) {
        if (CORNERS_TITLE.equals(panel.title())) {
            return CORNERS_LETTER;
        }
        char board = BoardPosition.boardOf(panel.centres().keySet().iterator().next());
        return board == BoardPosition.MAIN_BOARD ? MAIN_LETTER : Character.toUpperCase(board);
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
                    true,
                    allowed,
                    fractureYbump);
        }
        for (MapSegment segment : segments) {
            addPanel(
                    panels,
                    game,
                    segment.displayName(game),
                    segment.positions(),
                    segment.kind() == MapSegment.Kind.BOARD,
                    allowed,
                    fractureYbump);
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
            panels.add(new Panel(CORNERS_TITLE, corners, panels.size(), true));
        }
        return panels;
    }

    private static void addPanel(
            List<Panel> panels,
            Game game,
            String title,
            Set<String> positions,
            boolean wholeBoard,
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
            panels.add(new Panel(title, centres, panels.size(), wholeBoard));
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

    private static Map<String, Point> centredOnItself(Map<String, Point> centres) {
        long sumX = 0;
        long sumY = 0;
        for (Point centre : centres.values()) {
            sumX += centre.x;
            sumY += centre.y;
        }
        int count = Math.max(1, centres.size());
        return moved(centres, new Point((int) (-sumX / count), (int) (-sumY / count)));
    }

    private static Map<String, Point> moved(Map<String, Point> centres, Point offset) {
        Map<String, Point> moved = new LinkedHashMap<>();
        centres.forEach((position, centre) -> moved.put(position, translated(centre, offset)));
        return moved;
    }

    private static Point translated(Point point, Point offset) {
        return new Point(point.x + offset.x, point.y + offset.y);
    }

    private static HexPacker.Shape islandShape(Map<String, Point> island, int reach) {
        List<Point> centres = List.copyOf(island.values());
        return new HexPacker.Shape(
                centres, List.of(), HexPacker.withEnclosedHoles(HexReach.withBridges(centres, reach)));
    }

    static Layout layout(List<Group> groups, String header, Options options) {
        List<Cluster> clusters =
                groups.stream().map(group -> Cluster.of(group, options.reach())).toList();
        List<Placed> placed =
                options.style() == Style.BOXES ? boxLayout(clusters, options) : hexLayout(clusters, options);
        Rectangle content = contentBounds(placed);
        OverviewLegend legend =
                options.codes() && !groups.isEmpty() ? OverviewLegend.beside(content, legendEntries(groups)) : null;
        if (legend != null) {
            content = content.union(legend.bounds());
        }
        return new Layout(placed, legend, HexIslandStyle.Canvas.of(content, MAX_SIZE, header));
    }

    private static List<OverviewLegend.Entry> legendEntries(List<Group> groups) {
        return groups.stream()
                .map(group -> new OverviewLegend.Entry(group.code(), group.title(), group.color()))
                .toList();
    }

    private static Rectangle contentBounds(List<Placed> placed) {
        Rectangle bounds = HexIslandStyle.contentBounds(
                placed.stream().flatMap(box -> box.centres().values().stream()).toList(),
                placed.stream().map(Placed::label).toList());
        for (Placed box : placed) {
            if (box.frame() != null) {
                bounds = bounds.union(box.frame());
            }
        }
        return bounds;
    }

    private static List<Placed> hexLayout(List<Cluster> clusters, Options options) {
        List<Cluster> bySize = new ArrayList<>(clusters);
        bySize.sort(
                Comparator.comparingInt((Cluster cluster) -> -cluster.centres().size())
                        .thenComparing(cluster -> cluster.group().title()));
        List<HexPacker.Shape> shapes = bySize.stream()
                .map(cluster -> new HexPacker.Shape(
                        cluster.centres(),
                        HexIslandStyle.labelChoices(cluster.group().label(options), cluster.centres()),
                        cluster.footprint()))
                .toList();
        List<HexPacker.Placement> placements = HexPacker.pack(shapes, GROUP_GUTTER);
        List<Placed> placed = new ArrayList<>();
        for (int index = 0; index < bySize.size(); index++) {
            Cluster cluster = bySize.get(index);
            Point offset = placements.get(index).offset();
            placed.add(new Placed(
                    cluster.group(),
                    cluster.islands().stream()
                            .map(island -> moved(island, offset))
                            .toList(),
                    placements.get(index).label(),
                    null));
        }
        return placed;
    }

    private static List<Placed> boxLayout(List<Cluster> clusters, Options options) {
        List<BoxContent> contents = new ArrayList<>(clusters.stream()
                .map(cluster -> BoxContent.of(cluster, options))
                .toList());
        contents.sort(Comparator.comparingInt((BoxContent content) -> -content.size().height)
                .thenComparingInt(content -> -content.size().width)
                .thenComparing(content -> content.cluster().group().title()));
        BoxPacker.Packing packing =
                BoxPacker.pack(contents.stream().map(BoxContent::size).toList(), BOX_GAP);
        List<Placed> placed = new ArrayList<>();
        for (int index = 0; index < contents.size(); index++) {
            placed.add(contents.get(index).placedAt(packing.positions().get(index)));
        }
        return placed;
    }

    private record BoxContent(Cluster cluster, Rectangle hexes, Rectangle label, Dimension size) {

        static BoxContent of(Cluster cluster, Options options) {
            Rectangle hexes = HexIslandStyle.contentBounds(cluster.centres(), List.of());
            Rectangle label = HexIslandStyle.labelChoices(cluster.group().label(options), cluster.centres())
                    .getFirst();
            int width = Math.max(hexes.width, label.width) + 2 * BOX_PADDING;
            int height = label.height + BOX_LABEL_GAP + hexes.height + 2 * BOX_PADDING;
            return new BoxContent(cluster, hexes, label, new Dimension(width, height));
        }

        Placed placedAt(Point corner) {
            Rectangle frame = new Rectangle(corner.x, corner.y, size.width, size.height);
            int hexLeft = corner.x + (size.width - hexes.width) / 2;
            int hexTop = corner.y + BOX_PADDING + label.height + BOX_LABEL_GAP;
            Point offset = new Point(hexLeft - hexes.x, hexTop - hexes.y);
            Rectangle placedLabel = new Rectangle(
                    corner.x + (size.width - label.width) / 2, corner.y + BOX_PADDING, label.width, label.height);
            return new Placed(
                    cluster.group(),
                    cluster.islands().stream()
                            .map(island -> moved(island, offset))
                            .toList(),
                    placedLabel,
                    frame);
        }
    }

    private static BufferedImage render(
            Game game,
            List<Group> groups,
            Map<String, Tile> tiles,
            TileLayers tileLayers,
            List<TileStep> steps,
            String header,
            Options options) {
        Layout layout = layout(groups, header, options);
        BufferedImage image = layout.canvas().newImage();
        Graphics2D graphics = OverviewStyle.smoothGraphics(image);
        try {
            OverviewStyle.drawHeader(graphics, header);
            layout.canvas().enter(graphics);
            drawTiles(game, graphics, layout, tiles, tileLayers, steps);
            layout.placed().forEach(box -> drawFrame(graphics, box));
            layout.placed()
                    .forEach(box -> HexIslandStyle.drawLabel(
                            graphics, box.label(), box.group().label(options), box.color()));
            if (layout.legend() != null) {
                layout.legend().draw(graphics);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawFrame(Graphics2D graphics, Placed box) {
        Rectangle frame = box.frame();
        if (frame == null) {
            box.islands().forEach(island -> HexIslandStyle.drawOutline(graphics, island.values(), box.color()));
            return;
        }
        graphics.setColor(box.color());
        graphics.setStroke(new BasicStroke(BOX_STROKE));
        graphics.drawRoundRect(frame.x, frame.y, frame.width, frame.height, BOX_ARC, BOX_ARC);
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
                        HexIslandStyle.drawLayer(graphics, tileLayers, tile, centre, step);
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
