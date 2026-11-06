package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.image.draw.IconHalo;
import ti4.image.draw.OutlinedText;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class AttachmentsStep implements TileStepPainter {

    private static final int INNER_BORDER = 3;
    private static final int OUTER_BORDER = INNER_BORDER + 3;
    private static final int PADDING = OUTER_BORDER + 2;
    private static final int STACKED_LIFT = 36;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Tile tile = layer.tile();
        if (HighlightGuards.isHiddenOrHyperlane(scene, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        if (tile.getPlanetUnitHolders().isEmpty()) {
            HighlightGuards.fogOut(layer);
            return;
        }
        boolean anyAttachments = false;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            List<String> attachments = new ArrayList<>(planet.getAttachments());
            if (attachments.isEmpty()) {
                continue;
            }
            anyAttachments = true;
            drawAttachments(layer, planet, attachments);
        }
        if (!anyAttachments) {
            HighlightGuards.fogOut(layer);
        }
    }

    private static void drawAttachments(TileLayer layer, Planet planet, List<String> attachments) {
        int number = attachments.size();
        String chevronFile = ImageResources.general("misc_chevrons_basic.png");
        if (attachments.contains("attachment_tombofemphidia.png")) {
            chevronFile = ImageResources.general("misc_chevrons_toe.png");
        }
        BufferedImage source = ImageHelper.read(chevronFile);
        float scale = (float) Math.sqrt(TraitsStep.ICON_AREA / source.getWidth() / source.getHeight());
        IconHalo.HaloedIcon haloed =
                IconHalo.scaledTwoTone(chevronFile, source, scale, INNER_BORDER, OUTER_BORDER, PADDING);
        BufferedImage icon = haloed.icon();
        IconHalo halo = haloed.halo();
        int w = icon.getWidth();
        int h = icon.getHeight();

        Point position = TokenPlanetPlacement.holderCenter(planet, layer.tile());
        position = new Point(position.x - w / 2 + TILE_PADDING, position.y - h / 2 + TILE_PADDING);
        Graphics2D g = layer.g();
        if (number == 1) {
            halo.drawBehind(g, position.x, position.y);
            g.drawImage(icon, position.x, position.y, null);
            return;
        }
        halo.drawBehind(g, position.x, position.y - STACKED_LIFT);
        g.drawImage(icon, position.x, position.y - STACKED_LIFT, null);
        g.setColor(Color.WHITE);
        g.fillOval(position.x + w / 2 - 40, position.y + h / 2 - 8, 80, 80);
        g.setColor(Color.BLACK);
        g.fillOval(position.x + w / 2 - 36, position.y + h / 2 - 8 + 4, 72, 72);
        g.setColor(Color.WHITE);
        OutlinedText.drawCentered(
                g, "" + number, new Rectangle(position.x + w / 2 - 40, position.y + h / 2 - 8, 80, 80), Fonts.ti(48));
    }
}
