package ti4.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.service.image.FileUploadService;

public final class CompactOverviewGenerator {

    static final int GM_MAX_SIZE = 4000;
    static final int PLAYER_MAX_SIZE = 2000;
    static final int GAP = 120;
    static final int TITLE_BAND = 90;
    static final int HEADER = 70;
    private static final int TILE_PADDING = 100;
    private static final int TILE_IMAGE_SIZE = 600;
    private static final int CORNER_SPACING = 20;
    private static final int TITLE_MARGIN = 60;
    private static final FontMetrics TITLE_METRICS = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
            .createGraphics()
            .getFontMetrics(Storage.getFont32().deriveFont(TITLE_BAND * 0.6f));
    private static final String CORNERS_TITLE = "corners";
    private static final Set<String> CORNER_POSITIONS = Set.of("tl", "tr", "bl", "br");
    private static final List<TileStep> GM_STEPS =
            List.of(TileStep.Tile, TileStep.Extras, TileStep.Units, TileStep.TileNumber);
    private static final List<TileStep> PLAYER_STEPS = List.of(TileStep.Tile, TileStep.Units, TileStep.TileNumber);
    private static final List<Color> PANEL_COLORS = List.of(
            new Color(0, 170, 255),
            new Color(255, 140, 0),
            new Color(60, 200, 90),
            new Color(230, 60, 160),
            new Color(250, 220, 40),
            new Color(160, 90, 255),
            new Color(0, 210, 200),
            new Color(240, 70, 60));

    record Panel(String title, Map<String, Rectangle> hexes) {

        Rectangle content() {
            Rectangle content = null;
            for (Rectangle hex : hexes.values()) {
                content = content == null ? new Rectangle(hex) : content.union(hex);
            }
            return content == null ? new Rectangle() : content;
        }
    }

    record Placed(Panel panel, Rectangle content, int x, int y, int width) {

        int contentLeft() {
            return x + (width - content.width) / 2;
        }

        int height() {
            return content.height + TITLE_BAND;
        }
    }

    record Layout(List<Placed> placed, int width, int height, double scale) {

        int canvasWidth() {
            return Math.max(1, (int) (width * scale));
        }

        int canvasHeight() {
            return Math.max(1, (int) (height * scale));
        }
    }

    private CompactOverviewGenerator() {}

    public static FileUpload gmOverview(Game game) {
        return FileUploadService.createFileUpload(gmImage(game), game.getName() + "_overview", "webp");
    }

    public static FileUpload playerOverview(Game game, Player player, @Nullable GenericInteractionCreateEvent event) {
        return FileUploadService.createFileUpload(
                playerImage(game, player, event), game.getName() + "_my_overview", "webp");
    }

    static BufferedImage gmImage(Game game) {
        return render(
                game,
                gmPanels(game),
                new HashMap<>(game.getTileMap()),
                new TileGenerator(game, null, DisplayType.map),
                GM_STEPS,
                GM_MAX_SIZE,
                game.getName() + " - GM overview");
    }

    static BufferedImage playerImage(Game game, Player player, @Nullable GenericInteractionCreateEvent event) {
        Map<String, Tile> tiles = knownTiles(game, player);
        return render(
                game,
                playerPanels(game, player, tiles.keySet()),
                tiles,
                new TileGenerator(game, event, DisplayType.map, 0, "000", player),
                PLAYER_STEPS,
                PLAYER_MAX_SIZE,
                game.getName() + " - " + player.getFaction() + " overview");
    }

    static List<Panel> gmPanels(Game game) {
        return panels(game, MapSegment.all(game), true, game.getTileMap().keySet());
    }

    static List<Panel> playerPanels(Game game, Player player, Set<String> known) {
        return panels(game, MapSegment.visibleTo(game, player), MapSegment.mainMapVisibleTo(game, player), known);
    }

    static Map<String, Tile> knownTiles(Game game, Player player) {
        Set<String> visible = FoWHelper.fowFilter(game, player);
        Map<String, Tile> tiles = new HashMap<>();
        game.getTileMap().forEach((position, tile) -> {
            if (visible.contains(position)) {
                tiles.put(position, tile);
                return;
            }
            Tile remembered = player.buildFogTile(position, player);
            if (remembered != null && !isUnknownBlank(remembered, player)) {
                tiles.put(position, remembered);
            }
        });
        return tiles;
    }

    private static boolean isUnknownBlank(Tile tile, Player player) {
        return "0b".equals(tile.getTileID()) && StringUtils.isBlank(tile.getFogLabel(player));
    }

    private static List<Panel> panels(Game game, List<MapSegment> segments, boolean withMain, Set<String> allowed) {
        int fractureYbump = MapOverviewGenerator.fractureYbump(game);
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
        Map<String, Rectangle> corners = new LinkedHashMap<>();
        for (String corner : List.of("tl", "tr", "bl", "br")) {
            if (allowed.contains(corner) && game.getTileByPosition(corner) != null) {
                int x = corners.size() * (TileGenerator.TILE_WIDTH + CORNER_SPACING);
                corners.put(corner, new Rectangle(x, 0, TileGenerator.TILE_WIDTH, TileGenerator.TILE_HEIGHT));
            }
        }
        if (!corners.isEmpty()) {
            panels.add(new Panel(CORNERS_TITLE, corners));
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
        Map<String, Rectangle> hexes = new LinkedHashMap<>();
        for (String position : new TreeSet<>(positions)) {
            if (!allowed.contains(position) || CORNER_POSITIONS.contains(position.toLowerCase())) {
                continue;
            }
            Rectangle hex = MapFrame.hexBounds(game, position, fractureYbump, 0, 0);
            if (hex != null) {
                hexes.put(position, hex);
            }
        }
        if (!hexes.isEmpty()) {
            panels.add(new Panel(title, hexes));
        }
    }

    static Layout layout(List<Panel> panels, int maxSize) {
        List<Panel> bySize = new ArrayList<>(panels);
        bySize.sort(Comparator.comparingInt((Panel panel) -> panel.content().height)
                .reversed()
                .thenComparing(Panel::title));
        long area = 0;
        int widest = 0;
        for (Panel panel : bySize) {
            Rectangle content = panel.content();
            int panelWidth = panelWidth(panel, content);
            area += (long) (panelWidth + GAP) * (content.height + TITLE_BAND + GAP);
            widest = Math.max(widest, panelWidth);
        }
        int rowWidth = Math.max(widest, (int) (1.4 * Math.sqrt(area)));
        List<Placed> placed = new ArrayList<>();
        int x = GAP;
        int y = HEADER;
        int rowHeight = 0;
        int width = 0;
        for (Panel panel : bySize) {
            Rectangle content = panel.content();
            int panelWidth = panelWidth(panel, content);
            if (x > GAP && x + panelWidth > rowWidth + GAP) {
                x = GAP;
                y += rowHeight + GAP;
                rowHeight = 0;
            }
            Placed box = new Placed(panel, content, x, y, panelWidth);
            placed.add(box);
            x += box.width() + GAP;
            rowHeight = Math.max(rowHeight, box.height());
            width = Math.max(width, x);
        }
        int height = y + rowHeight + GAP;
        width = Math.max(width, GAP * 2);
        double scale = Math.min(1.0, Math.min((double) maxSize / width, (double) maxSize / height));
        return new Layout(placed, width, height, scale);
    }

    private static int panelWidth(Panel panel, Rectangle content) {
        return Math.max(content.width, TITLE_METRICS.stringWidth(panel.title()) + TITLE_MARGIN);
    }

    private static BufferedImage render(
            Game game,
            List<Panel> panels,
            Map<String, Tile> tiles,
            TileGenerator tileGenerator,
            List<TileStep> steps,
            int maxSize,
            String header) {
        Layout layout = layout(panels, maxSize);
        BufferedImage image =
                new BufferedImage(layout.canvasWidth(), layout.canvasHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawPanelFrames(graphics, layout);
            drawTiles(game, graphics, layout, tiles, tileGenerator, steps);
            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            graphics.drawString(header, 10, 34);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawPanelFrames(Graphics2D graphics, Layout layout) {
        double scale = layout.scale();
        Font titleFont = Storage.getFont32().deriveFont((float) Math.max(16, TITLE_BAND * 0.6 * scale));
        for (int index = 0; index < layout.placed().size(); index++) {
            Placed box = layout.placed().get(index);
            Color color = PANEL_COLORS.get(index % PANEL_COLORS.size());
            int left = (int) Math.round(box.x() * scale);
            int top = (int) Math.round(box.y() * scale);
            int width = (int) Math.round(box.width() * scale);
            int height = (int) Math.round(box.height() * scale);
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
            graphics.setStroke(new BasicStroke((float) Math.max(2, 6 * scale)));
            graphics.drawRoundRect(left, top, width, height, 16, 16);
            graphics.setFont(titleFont);
            DrawingUtil.superDrawString(
                    graphics,
                    box.panel().title(),
                    left + width / 2,
                    top + (int) Math.round(TITLE_BAND * scale / 2),
                    color,
                    MapGenerator.HorizontalAlign.Center,
                    MapGenerator.VerticalAlign.Center,
                    new BasicStroke(4),
                    Color.BLACK);
        }
    }

    private static void drawTiles(
            Game game,
            Graphics2D graphics,
            Layout layout,
            Map<String, Tile> tiles,
            TileGenerator tileGenerator,
            List<TileStep> steps) {
        double scale = layout.scale();
        int drawnSize = (int) Math.ceil(TILE_IMAGE_SIZE * scale);
        for (TileStep step : steps) {
            for (Placed box : layout.placed()) {
                box.panel().hexes().forEach((position, hex) -> {
                    Tile tile = tiles.get(position);
                    if (tile == null) {
                        return;
                    }
                    int rawX = box.contentLeft() + hex.x - box.content().x - TILE_PADDING;
                    int rawY = box.y() + TITLE_BAND + hex.y - box.content().y - TILE_PADDING;
                    try {
                        BufferedImage tileImage = tileGenerator.draw(tile, step);
                        graphics.drawImage(
                                tileImage,
                                (int) Math.round(rawX * scale),
                                (int) Math.round(rawY * scale),
                                drawnSize,
                                drawnSize,
                                null);
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
