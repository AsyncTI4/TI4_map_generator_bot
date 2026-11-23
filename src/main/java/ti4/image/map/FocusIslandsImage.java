package ti4.image.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageUploads;
import ti4.image.tile.TileGeometry;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;

public final class FocusIslandsImage {

    static final int MAX_SIZE = 2400;
    private static final int MARGIN = 60;
    private static final int HEADER = 50;
    private static final int OUTLINE_WIDTH = 10;
    private static final int LABEL_HEIGHT = 80;
    private static final int LABEL_PADDING = 30;
    private static final int LABEL_LIFT = 6;
    private static final int BADGE_RADIUS = 24;
    private static final int MAX_BADGES = 3;
    private static final List<Double> BADGE_EDGE_STOPS = List.of(0.2, 0.5, 0.8);
    private static final Color LABEL_FILL = new Color(0, 0, 0, 200);
    private static final Font LABEL_FONT = Fonts.ti(32).deriveFont(56f);
    private static final Font BADGE_FONT = Fonts.ti(32).deriveFont(30f);
    private static final FontMetrics LABEL_METRICS =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics().getFontMetrics(LABEL_FONT);
    private static final List<TileStep> STEPS_UNDER_SHADE = List.of(TileStep.Tile, TileStep.Extras, TileStep.Units);

    record PlacedIsland(FocusIslands.Island island, Color color, Map<String, Point> centres, Rectangle label) {}

    private FocusIslandsImage() {}

    public static FileUpload upload(Game game, Player target, Set<String> lit, int distance) {
        return ImageUploads.createFileUpload(
                image(game, target, lit, distance), game.getName() + "_" + target.getFaction() + "_area", "webp");
    }

    static BufferedImage image(Game game, Player target, Set<String> lit, int distance) {
        List<PlacedIsland> placed = place(FocusIslands.of(game, target, lit));
        String header = game.getName() + " - " + target.getFaction() + " area - distance " + distance;
        Rectangle content = contentBounds(placed);
        int contentWidth = content.width + 2 * MARGIN;
        int contentHeight = content.height + 2 * MARGIN;
        double scale = Math.min(1.0, (double) MAX_SIZE / Math.max(contentWidth, contentHeight));
        int width = Math.max((int) Math.ceil(contentWidth * scale), headerWidth(header));
        int height = HEADER + (int) Math.ceil(contentHeight * scale);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = OverviewStyle.smoothGraphics(image);
        try {
            OverviewStyle.drawHeader(graphics, header);
            AffineTransform transform = new AffineTransform();
            transform.translate(0, HEADER);
            transform.scale(scale, scale);
            transform.translate(MARGIN - content.x, MARGIN - content.y);
            graphics.transform(transform);
            drawTiles(game, graphics, placed);
            placed.forEach(island -> drawOutline(graphics, island));
            Map<String, Color> colorByLetter = new HashMap<>();
            placed.forEach(island -> colorByLetter.put(island.island().letter(), island.color()));
            placed.forEach(island -> drawBadges(graphics, island, colorByLetter));
            placed.forEach(island -> drawLabel(graphics, island));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    static List<PlacedIsland> place(List<FocusIslands.Island> islands) {
        List<HexPacker.Shape> shapes = new ArrayList<>();
        List<Map<String, Point>> localCentres = new ArrayList<>();
        for (FocusIslands.Island island : islands) {
            Map<String, Point> centres = new LinkedHashMap<>();
            island.cells()
                    .forEach((position, cell) -> centres.put(
                            position,
                            new Point(cell.x * FocusIslands.COLUMN_STEP, cell.y * FocusIslands.HALF_ROW_STEP)));
            localCentres.add(centres);
            shapes.add(new HexPacker.Shape(List.copyOf(centres.values()), labelChoices(island.title(), centres)));
        }
        List<HexPacker.Placement> placements = HexPacker.pack(shapes);
        List<PlacedIsland> placed = new ArrayList<>();
        for (int index = 0; index < islands.size(); index++) {
            Point offset = placements.get(index).offset();
            Map<String, Point> centres = new LinkedHashMap<>();
            localCentres
                    .get(index)
                    .forEach((position, local) ->
                            centres.put(position, new Point(local.x + offset.x, local.y + offset.y)));
            placed.add(new PlacedIsland(
                    islands.get(index),
                    OverviewStyle.color(index),
                    centres,
                    placements.get(index).label()));
        }
        return placed;
    }

    private static List<Rectangle> labelChoices(String title, Map<String, Point> centres) {
        int width = LABEL_METRICS.stringWidth(title) + 2 * LABEL_PADDING;
        Point top = extreme(centres, Comparator.comparingInt((Point centre) -> centre.y)
                .thenComparingInt(centre -> centre.x));
        Point bottom = extreme(centres, Comparator.comparingInt((Point centre) -> -centre.y)
                .thenComparingInt(centre -> centre.x));
        Point left = extreme(centres, Comparator.comparingInt((Point centre) -> centre.x)
                .thenComparingInt(centre -> centre.y));
        Point right = extreme(centres, Comparator.comparingInt((Point centre) -> -centre.x)
                .thenComparingInt(centre -> centre.y));
        int besideY = LABEL_HEIGHT / 2;
        return List.of(
                new Rectangle(
                        top.x - width / 2,
                        top.y - HexPacker.HEX_HALF_HEIGHT - LABEL_HEIGHT - LABEL_LIFT,
                        width,
                        LABEL_HEIGHT),
                new Rectangle(
                        bottom.x - width / 2, bottom.y + HexPacker.HEX_HALF_HEIGHT + LABEL_LIFT, width, LABEL_HEIGHT),
                new Rectangle(
                        left.x - HexPacker.HEX_HALF_WIDTH - LABEL_LIFT - width,
                        left.y - besideY,
                        width,
                        LABEL_HEIGHT),
                new Rectangle(
                        right.x + HexPacker.HEX_HALF_WIDTH + LABEL_LIFT, right.y - besideY, width, LABEL_HEIGHT));
    }

    private static Point extreme(Map<String, Point> centres, Comparator<Point> order) {
        return centres.values().stream().min(order).orElse(new Point(0, 0));
    }

    static Rectangle contentBounds(List<PlacedIsland> placed) {
        Rectangle bounds = null;
        for (PlacedIsland island : placed) {
            bounds = bounds == null ? new Rectangle(island.label()) : bounds.union(island.label());
            for (Point centre : island.centres().values()) {
                bounds = bounds.union(HexPacker.hexBox(centre));
            }
        }
        return bounds == null ? new Rectangle(0, 0, 1, 1) : bounds;
    }

    private static int headerWidth(String header) {
        Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            return probe.getFontMetrics(Fonts.ti(32)).stringWidth(header) + 2 * MARGIN;
        } finally {
            probe.dispose();
        }
    }

    private static void drawTiles(Game game, Graphics2D graphics, List<PlacedIsland> placed) {
        TileLayers tileLayers = TileLayers.of(game, null, DisplayType.map);
        STEPS_UNDER_SHADE.forEach(step -> drawStep(game, graphics, placed, tileLayers, step));
        graphics.setColor(BoardSection.OUT_OF_FOCUS_SHADE);
        for (PlacedIsland island : placed) {
            for (String position : island.island().dimmed()) {
                graphics.fillPolygon(
                        MapFrame.hexPolygon(tileRectangle(island.centres().get(position))));
            }
        }
        drawStep(game, graphics, placed, tileLayers, TileStep.TileNumber);
    }

    private static void drawStep(
            Game game, Graphics2D graphics, List<PlacedIsland> placed, TileLayers tileLayers, TileStep step) {
        for (PlacedIsland island : placed) {
            island.centres().forEach((position, centre) -> {
                Tile tile = game.getTileByPosition(position);
                if (tile == null) {
                    return;
                }
                Rectangle hex = tileRectangle(centre);
                try {
                    graphics.drawImage(
                            tileLayers.draw(tile, step),
                            hex.x - TileGeometry.TILE_PADDING,
                            hex.y - TileGeometry.TILE_PADDING,
                            null);
                } catch (Exception e) {
                    BotLogger.error(
                            "Focus islands could not draw tile " + tile.getTileID() + " at " + position + " in "
                                    + game.getName(),
                            e);
                }
            });
        }
    }

    private static Rectangle tileRectangle(Point centre) {
        return new Rectangle(
                centre.x - TileGeometry.TILE_WIDTH / 2,
                centre.y - TileGeometry.TILE_HEIGHT / 2,
                TileGeometry.TILE_WIDTH,
                TileGeometry.TILE_HEIGHT);
    }

    private static void drawOutline(Graphics2D graphics, PlacedIsland island) {
        Set<Point> occupied = new HashSet<>(island.centres().values());
        graphics.setColor(island.color());
        graphics.setStroke(new BasicStroke(OUTLINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Point centre : island.centres().values()) {
            Polygon hex = MapFrame.hexPolygon(tileRectangle(centre));
            for (int edge = 0; edge < 6; edge++) {
                Point step = HexPacker.NEIGHBOUR_OFFSETS.get(edge);
                if (occupied.contains(new Point(centre.x + step.x, centre.y + step.y))) {
                    continue;
                }
                int next = (edge + 1) % 6;
                graphics.drawLine(hex.xpoints[edge], hex.ypoints[edge], hex.xpoints[next], hex.ypoints[next]);
            }
        }
    }

    private static void drawBadges(Graphics2D graphics, PlacedIsland island, Map<String, Color> colorByLetter) {
        graphics.setFont(BADGE_FONT);
        island.island().badges().forEach((position, letters) -> {
            Point centre = island.centres().get(position);
            if (centre == null) {
                return;
            }
            Polygon hex = MapFrame.hexPolygon(tileRectangle(centre));
            List<String> shown = badgeTexts(letters);
            for (int index = 0; index < shown.size(); index++) {
                String letter = shown.get(index);
                double stop = BADGE_EDGE_STOPS.get(index);
                int x = (int) Math.round(hex.xpoints[0] + stop * (hex.xpoints[5] - hex.xpoints[0]));
                int y = (int) Math.round(hex.ypoints[0] + stop * (hex.ypoints[5] - hex.ypoints[0]));
                graphics.setColor(colorByLetter.getOrDefault(letter, Color.DARK_GRAY));
                graphics.fillOval(x - BADGE_RADIUS, y - BADGE_RADIUS, 2 * BADGE_RADIUS, 2 * BADGE_RADIUS);
                graphics.setColor(Color.BLACK);
                graphics.setStroke(new BasicStroke(4));
                graphics.drawOval(x - BADGE_RADIUS, y - BADGE_RADIUS, 2 * BADGE_RADIUS, 2 * BADGE_RADIUS);
                OutlinedText.draw(
                        graphics,
                        letter,
                        x,
                        y,
                        Color.WHITE,
                        HorizontalAlign.Center,
                        VerticalAlign.Center,
                        new BasicStroke(4),
                        Color.BLACK);
            }
        });
    }

    static List<String> badgeTexts(List<String> letters) {
        if (letters.size() <= MAX_BADGES) {
            return letters;
        }
        List<String> shown = new ArrayList<>(letters.subList(0, MAX_BADGES - 1));
        shown.add("+" + (letters.size() - MAX_BADGES + 1));
        return shown;
    }

    private static void drawLabel(Graphics2D graphics, PlacedIsland island) {
        Rectangle label = island.label();
        graphics.setColor(LABEL_FILL);
        graphics.fillRoundRect(label.x, label.y, label.width, label.height, LABEL_HEIGHT / 2, LABEL_HEIGHT / 2);
        graphics.setColor(island.color());
        graphics.setStroke(new BasicStroke(5));
        graphics.drawRoundRect(label.x, label.y, label.width, label.height, LABEL_HEIGHT / 2, LABEL_HEIGHT / 2);
        graphics.setFont(LABEL_FONT);
        OutlinedText.draw(
                graphics,
                island.island().title(),
                label.x + label.width / 2,
                label.y + label.height / 2,
                island.color(),
                HorizontalAlign.Center,
                VerticalAlign.Center,
                new BasicStroke(4),
                Color.BLACK);
    }
}
