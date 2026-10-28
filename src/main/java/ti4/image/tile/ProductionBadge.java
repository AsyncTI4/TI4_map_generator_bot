package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

record ProductionBadge(int production, int capacity, int capacityUsed, int ignoredFighters) {

    private static final Point ANCHOR = new Point(255, 295);
    private static final int ANCHOR_Y_OFFSET = -290;
    private static final int WIDE_X_OFFSET = -15;
    private static final int FOUR_HOLDER_X_OFFSET = -155;
    private static final Set<String> WIDE_LAYOUT_TILE_IDS = Set.of("25", "26", "64");
    private static final BasicStroke CAPACITY_OUTLINE = Strokes.STROKE_4;

    static ProductionBadge measure(Game game, Tile tile) {
        int production = 0;
        int capacity = 0;
        int capacityUsed = 0;
        int ignoredFighters = 0;
        for (Player player : game.getRealPlayers()) {
            production = Math.max(production, Helper.getProductionValue(player, game, tile, false));
            if (capacity == 0 && capacityUsed == 0) {
                int[] fleetAndCapacity = ButtonHelper.fleetAndCapacity(player, game, tile, false);
                capacityUsed = fleetAndCapacity[1];
                capacity = fleetAndCapacity[2];
                ignoredFighters = fleetAndCapacity[3];
            }
        }
        return new ProductionBadge(production, capacity, capacityUsed, ignoredFighters);
    }

    void draw(Tile tile, Graphics2D g) {
        if (production > 0) {
            drawGear(tile, g);
        }
        if (capacityUsed > 0 || capacity > 0 || ignoredFighters > 0) {
            drawCapacity(tile, g);
        }
    }

    private static int xOffset(Tile tile) {
        boolean wide = tile.getUnitHolders().size() != 4 || WIDE_LAYOUT_TILE_IDS.contains(tile.getTileID());
        return wide ? WIDE_X_OFFSET : FOUR_HOLDER_X_OFFSET;
    }

    private void drawGear(Tile tile, Graphics2D g) {
        int textModifier = 0;
        if (production == 1) {
            textModifier = 7;
        }
        if (production > 9) {
            textModifier = -5;
        }
        if (production == 11) {
            textModifier = 0;
        }
        BufferedImage gearImage = ImageHelper.readScaled(ImageResources.tile("production_representation.png"), 64, 64);
        int xMod = xOffset(tile);
        g.drawImage(
                gearImage, TILE_PADDING + ANCHOR.x + xMod - 29, TILE_PADDING + ANCHOR.y + ANCHOR_Y_OFFSET - 4, null);
        g.setFont(Fonts.ti(35));
        g.drawString(
                production + "",
                TILE_PADDING + ANCHOR.x + xMod + 15 + textModifier - 25,
                TILE_PADDING + ANCHOR.y + ANCHOR_Y_OFFSET + 40);
    }

    private void drawCapacity(Tile tile, Graphics2D g) {
        BufferedImage carrierImage = ImageHelper.readScaled(ImageResources.tile("capacity_representation.png"), 64, 21);
        int xMod = xOffset(tile);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(6));

        int gearX = TILE_PADDING + ANCHOR.x + xMod - 29;
        int gearY = TILE_PADDING + ANCHOR.y + ANCHOR_Y_OFFSET + 5;
        if (production == 0) {
            gearX -= 27;
            gearY -= 55;
        }

        g.drawImage(carrierImage, gearX + 24, gearY + 60, null);
        g.setColor(Color.WHITE);
        g.setFont(Fonts.ti(12));
        String msg = capacityUsed + " / " + capacity;
        if (ignoredFighters > 0) {
            msg = capacityUsed + " / " + (capacity + ignoredFighters) + "*";
        }
        OutlinedText.draw(
                g,
                msg,
                gearX + 39 + 17,
                gearY + 95,
                Color.WHITE,
                HorizontalAlign.Center,
                null,
                CAPACITY_OUTLINE,
                Color.BLACK);
    }
}
