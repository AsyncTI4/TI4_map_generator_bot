package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.PdsCoverage;
import ti4.helpers.PdsCoverageHelper;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PlayerTokens;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class SpaceCannonStep implements TileStepPainter {

    private record DiceFonts(Font big, Font small) {}

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        Map<String, PdsCoverage> pdsCoverageMap = PdsCoverageHelper.calculatePdsCoverage(game, tile);
        if (HighlightGuards.isTileImageMissing(tile)) {
            return;
        }

        Map<String, List<Integer>> pdsDice = new HashMap<>();
        Map<String, PdsCoverage> pdsCoverageByFaction = new HashMap<>();
        if (pdsCoverageMap != null) {
            for (Player player : game.getRealPlayers()) {
                PdsCoverage coverage = pdsCoverageMap.get(player.getFaction());
                if (coverage != null) {
                    pdsDice.put(player.getUserID(), coverage.getDiceValues());
                    pdsCoverageByFaction.put(player.getFaction(), coverage);
                }
            }
        }

        if (pdsDice.isEmpty()) {
            HighlightGuards.fogOut(layer);
            return;
        }
        float scale = pdsDice.size() >= 3 ? 6.0f / pdsDice.size() : 3.0f;
        DiceFonts fonts = fontsFor(pdsDice.size());
        int x = TILE_PADDING + layer.spiralOffsetX() + (int) ((345 - 73 * scale) / 2);
        int y = TILE_PADDING + layer.spiralOffsetY() + (int) ((300 - pdsDice.size() * 48 * scale) / 2);
        for (Map.Entry<String, List<Integer>> entry : pdsDice.entrySet()) {
            Player player = game.getPlayer(entry.getKey());
            PdsCoverage coverage = pdsCoverageByFaction.get(player.getFaction());
            drawCoverage(layer.g(), player, coverage, entry.getValue(), fonts, scale, x, y);
            y += (int) (48 * scale);
        }
    }

    private static DiceFonts fontsFor(int players) {
        return switch (players) {
            case 1, 2 -> new DiceFonts(Fonts.ti(64), Fonts.ti(32));
            case 3 -> new DiceFonts(Fonts.ti(40), Fonts.ti(20));
            case 4 -> new DiceFonts(Fonts.ti(32), Fonts.ti(16));
            case 5 -> new DiceFonts(Fonts.ti(24), Fonts.ti(12));
            default -> new DiceFonts(Fonts.ti(16), Fonts.ti(8));
        };
    }

    private static void drawCoverage(
            Graphics2D g,
            Player player,
            PdsCoverage coverage,
            List<Integer> dice,
            DiceFonts fonts,
            float scale,
            int x,
            int y) {
        int numberOfDice = coverage.getCount();
        boolean rerolls = coverage.isHasRerolls();
        float expectedHits = coverage.getExpected();
        if ("_wht.png".equals(Mapper.getColor(player.getColorID()).blackWhiteFileSuffix())) {
            g.setColor(Color.WHITE);
        } else {
            g.setColor(Color.BLACK);
        }
        BufferedImage token =
                ImageHelper.readScaled(ImageResources.commandToken(Mapper.getControlID(player.getColor())), scale);
        PlayerTokens.drawControlToken(g, token, player, x, y, false, scale / 2);
        OutlinedText.drawCentered(
                g,
                numberOfDice + (rerolls ? "*" : ""),
                new Rectangle(
                        Math.round(x + 6 * scale),
                        Math.round(y + 12 * scale),
                        Math.round(61 * scale / 2),
                        Math.round(24 * scale * 2 / 3)),
                fonts.big());
        OutlinedText.drawCentered(
                g,
                "(" + expectedHits + ")",
                new Rectangle(
                        Math.round(x + 6 * scale),
                        Math.round(y + 12 * scale + 24 * scale * 2 / 3),
                        Math.round(61 * scale / 2),
                        Math.round(24 * scale / 3)),
                fonts.small());
        if (numberOfDice >= 5) {
            drawDiceRows(g, dice, numberOfDice, 3, 6, 36, fonts.small(), scale, x, y);
        } else if (numberOfDice >= 3) {
            drawDiceRows(g, dice, numberOfDice, 2, 12, 24, fonts.small(), scale, x, y);
        } else {
            OutlinedText.drawCentered(
                    g,
                    joined(dice),
                    new Rectangle(
                            Math.round(x + 73 * scale / 2), y, Math.round(73 * scale / 2), Math.round(48 * scale)),
                    fonts.small());
        }
    }

    private static void drawDiceRows(
            Graphics2D g,
            List<Integer> dice,
            int numberOfDice,
            int rows,
            int top,
            int height,
            Font font,
            float scale,
            int x,
            int y) {
        for (int row = 0; row < rows; row++) {
            int from = row * numberOfDice / rows;
            int to = (row + 1) * numberOfDice / rows;
            String text = joined(dice.subList(from, to)) + (row < rows - 1 ? "," : "");
            OutlinedText.drawCentered(
                    g,
                    text,
                    new Rectangle(
                            Math.round(x + 73 * scale / 2),
                            Math.round(y + top * scale + height * scale * row / rows),
                            Math.round(73 * scale / 2),
                            Math.round(height * scale / rows)),
                    font);
        }
    }

    private static String joined(List<Integer> dice) {
        return dice.stream().map(Object::toString).collect(Collectors.joining(","));
    }
}
