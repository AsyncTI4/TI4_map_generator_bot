package ti4.image.tile;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Throwaway visual check for the one-way entry hint on the neighbouring tile; not for commit.
// -Dpreview.out=<dir> -Dpreview.tag=before|after ; the "after" run also builds sheet.png and zoom.png.
class HyperlaneHintPreviewScratchTest extends BaseTi4Test {

    private static final String OUT = System.getProperty("preview.out", "target/hl-preview");
    private static final String TAG = System.getProperty("preview.tag", "after");
    private static final List<String> SYSTEMS = List.of("19", "20", "21", "22", "23", "24", "25");

    private record Scenario(String name, Map<String, String> hyperlanes) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("A straight N->S", Map.of("000", stored(new int[][] {{0, 3}}))),
            new Scenario("B curve SW->NW + 2-way NE-S", Map.of("000", stored(new int[][] {{4, 5}, {1, 3}, {3, 1}}))),
            new Scenario(
                    "C chained into a hyperlane",
                    Map.of("000", stored(new int[][] {{0, 3}}), "104", stored(new int[][] {{0, 3}}))),
            new Scenario(
                    "D roundabout N>SE>SW",
                    Map.of("000", stored(new int[][] {{0, 0}, {2, 2}, {4, 4}, {0, 2}, {0, 4}, {2, 4}}))));

    @Test
    void render() throws Exception {
        File dir = new File(OUT);
        dir.mkdirs();
        for (int s = 0; s < SCENARIOS.size(); s++) {
            ImageIO.write(miniMap(SCENARIOS.get(s)), "png", new File(dir, TAG + "-map" + s + ".png"));
        }
        if ("after".equals(TAG) && new File(dir, "before-map0.png").exists()) {
            sheet(dir);
            zoom(dir);
        }
    }

    private static BufferedImage miniMap(Scenario scenario) {
        Game game = new Game();
        List<String> positions = new java.util.ArrayList<>();
        positions.add("000");
        positions.addAll(MapPositionMapper.getAdjacentTilePositions("000"));
        for (int i = 0; i < positions.size(); i++) {
            String position = positions.get(i);
            boolean hyperlane = scenario.hyperlanes.containsKey(position);
            game.setTile(new Tile(hyperlane ? "hl" : SYSTEMS.get(i), position));
            if (hyperlane) game.getCustomHyperlaneData().put(position, scenario.hyperlanes.get(position));
        }
        Map<String, Point> points = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
        for (String position : positions) {
            Point p = MapPositionMapper.getTilePosition(position);
            points.put(position, p);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        BufferedImage map = new BufferedImage(
                maxX - minX + TileGeometry.TILE_WIDTH, maxY - minY + TileGeometry.TILE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(20, 20, 25));
        g.fillRect(0, 0, map.getWidth(), map.getHeight());
        TileLayers layers = TileLayers.of(game, null, null);
        for (TileStep step : List.of(TileStep.Tile, TileStep.Extras)) {
            for (String position : positions) {
                Point p = points.get(position);
                layers.paint(
                        game.getTileByPosition(position),
                        step,
                        g,
                        p.x - minX - TileGeometry.TILE_PADDING,
                        p.y - minY - TileGeometry.TILE_PADDING);
            }
        }
        g.dispose();
        return map;
    }

    private static void sheet(File dir) throws Exception {
        double scale = 0.5;
        BufferedImage first = ImageIO.read(new File(dir, "after-map0.png"));
        int w = (int) (first.getWidth() * scale), h = (int) (first.getHeight() * scale), header = 34, gap = 16;
        BufferedImage sheet = new BufferedImage(
                2 * w + 3 * gap, SCENARIOS.size() * (h + header) + gap, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(30, 30, 35));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        for (int s = 0; s < SCENARIOS.size(); s++) {
            int y = s * (h + header);
            g.setColor(Color.WHITE);
            g.drawString(SCENARIOS.get(s).name + "   (left: before, right: after)", gap, y + 24);
            for (int col = 0; col < 2; col++) {
                BufferedImage img = ImageIO.read(new File(dir, (col == 0 ? "before" : "after") + "-map" + s + ".png"));
                g.drawImage(img, gap + col * (w + gap), y + header, w, h, null);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, "sheet.png"));
    }

    // 3x crop around the centre tile's south edge (scenario A) and SW edge (scenario D).
    private static void zoom(File dir) throws Exception {
        BufferedImage map = ImageIO.read(new File(dir, "after-map0.png"));
        Point centre = new Point(map.getWidth() / 2, map.getHeight() / 2);
        int[][] boxes = {
            {0, centre.x - 60, centre.y + 150 - 50},
            {3, centre.x - 129 - 60, centre.y + 75 - 50}
        };
        int scale = 3, cw = 120 * scale, ch = 100 * scale, gap = 20;
        BufferedImage out = new BufferedImage(2 * cw + 3 * gap, boxes.length * (ch + gap) + gap, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(30, 30, 35));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int r = 0; r < boxes.length; r++) {
            for (int col = 0; col < 2; col++) {
                BufferedImage img =
                        ImageIO.read(new File(dir, (col == 0 ? "before" : "after") + "-map" + boxes[r][0] + ".png"));
                BufferedImage crop = img.getSubimage(boxes[r][1], boxes[r][2], 120, 100);
                g.drawImage(crop, gap + col * (cw + gap), gap + r * (ch + gap), cw, ch, null);
            }
        }
        g.dispose();
        ImageIO.write(out, "png", new File(dir, "zoom.png"));
    }

    private static String stored(int[][] links) {
        int[][] m = new int[6][6];
        for (int[] link : links) m[link[0]][link[1]] = 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(';');
            for (int j = 0; j < 6; j++) {
                if (j > 0) sb.append(',');
                sb.append(m[i][j]);
            }
        }
        return sb.toString();
    }
}
