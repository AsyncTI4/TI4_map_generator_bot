package ti4.image.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
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
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;

public final class FocusIslandsImage {

    static final int MAX_SIZE = 2400;
    private static final int BADGE_RADIUS = 24;
    private static final int MAX_BADGES = 3;
    private static final List<Double> BADGE_EDGE_STOPS = List.of(0.2, 0.5, 0.8);
    private static final Font BADGE_FONT = Fonts.ti(32).deriveFont(30f);
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
        HexIslandStyle.Canvas canvas = HexIslandStyle.Canvas.of(contentBounds(placed), MAX_SIZE, header);
        BufferedImage image = canvas.newImage();
        Graphics2D graphics = OverviewStyle.smoothGraphics(image);
        try {
            OverviewStyle.drawHeader(graphics, header);
            canvas.enter(graphics);
            drawTiles(game, graphics, placed);
            placed.forEach(island ->
                    HexIslandStyle.drawOutline(graphics, island.centres().values(), island.color()));
            Map<String, Color> colorByLetter = new HashMap<>();
            placed.forEach(island -> colorByLetter.put(island.island().letter(), island.color()));
            placed.forEach(island -> drawBadges(graphics, island, colorByLetter));
            placed.forEach(island -> HexIslandStyle.drawLabel(
                    graphics, island.label(), island.island().title(), island.color()));
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
            shapes.add(new HexPacker.Shape(
                    List.copyOf(centres.values()), HexIslandStyle.labelChoices(island.title(), centres.values())));
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

    static Rectangle contentBounds(List<PlacedIsland> placed) {
        return HexIslandStyle.contentBounds(
                placed.stream()
                        .flatMap(island -> island.centres().values().stream())
                        .toList(),
                placed.stream().map(PlacedIsland::label).toList());
    }

    private static void drawTiles(Game game, Graphics2D graphics, List<PlacedIsland> placed) {
        TileLayers tileLayers = TileLayers.of(game, null, DisplayType.map);
        STEPS_UNDER_SHADE.forEach(step -> drawStep(game, graphics, placed, tileLayers, step));
        graphics.setColor(BoardSection.OUT_OF_FOCUS_SHADE);
        for (PlacedIsland island : placed) {
            for (String position : island.island().dimmed()) {
                graphics.fillPolygon(MapFrame.hexPolygon(
                        HexIslandStyle.tileRectangle(island.centres().get(position))));
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
                try {
                    HexIslandStyle.drawLayer(graphics, tileLayers, tile, centre, step);
                } catch (Exception e) {
                    BotLogger.error(
                            "Focus islands could not draw tile " + tile.getTileID() + " at " + position + " in "
                                    + game.getName(),
                            e);
                }
            });
        }
    }

    private static void drawBadges(Graphics2D graphics, PlacedIsland island, Map<String, Color> colorByLetter) {
        graphics.setFont(BADGE_FONT);
        island.island().badges().forEach((position, letters) -> {
            Point centre = island.centres().get(position);
            if (centre == null) {
                return;
            }
            Polygon hex = MapFrame.hexPolygon(HexIslandStyle.tileRectangle(centre));
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
}
