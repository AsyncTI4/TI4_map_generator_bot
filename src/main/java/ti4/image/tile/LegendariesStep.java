package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_PADDING;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.image.draw.IconHalo;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class LegendariesStep implements TileStepPainter {

    private static final float COUNCIL_SCALE = 2.0f;
    private static final int COUNCIL_BORDER = 3;
    private static final int COUNCIL_PADDING = COUNCIL_BORDER + 2;
    private static final int SPREAD = 60;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (HighlightGuards.isHiddenOrHyperlane(scene, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        boolean isLegendary = ButtonHelper.isTileLegendary(tile) || tile.isMecatol(game);
        if (!isLegendary) {
            HighlightGuards.fogOut(layer);
        } else if (tile.isMecatol(game)) {
            drawCouncil(layer.g());
        } else {
            drawLegendaryMarks(layer, legendaryPlanetCount(tile));
        }
    }

    private static int legendaryPlanetCount(Tile tile) {
        int number = 0;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            number += (planet.isLegendary() ? 1 : 0);
        }
        return number;
    }

    private static void drawCouncil(Graphics2D g) {
        String councilFile = ImageResources.faction("agenda.png");
        BufferedImage council = ImageHelper.readScaled(councilFile, COUNCIL_SCALE);
        if (council == null) return;

        int w = council.getWidth();
        BufferedImage background =
                IconHalo.blackRing(councilFile, COUNCIL_SCALE, council, COUNCIL_BORDER, COUNCIL_PADDING);
        int x = TILE_PADDING + (TILE_WIDTH - w) / 2;
        int y = TILE_PADDING + (TILE_HEIGHT - w) / 2;
        g.drawImage(background, x - COUNCIL_PADDING, y - COUNCIL_PADDING, null);
        g.drawImage(council, x, y, null);
    }

    private static void drawLegendaryMarks(TileLayer layer, int number) {
        int x = TILE_PADDING + layer.spiralOffsetX();
        int y = TILE_PADDING + layer.spiralOffsetY();
        String legendaryFile = ImageResources.general("Legendary_complete.png");
        BufferedImage mark = ImageHelper.readScaled(legendaryFile, 0.5f);
        if (number >= 2) {
            mark = ImageHelper.scale(mark, (float) Math.sqrt(Math.sqrt(1.0f / number)));
            x += (TILE_WIDTH - mark.getWidth()) / 2;
            y += (TILE_HEIGHT - mark.getHeight()) / 2;
            for (int i = 0; i < number; i++) {
                layer.g()
                        .drawImage(
                                mark,
                                x - SPREAD / 2 + i * SPREAD / (number - 1),
                                y - SPREAD / 2 + i * SPREAD / (number - 1),
                                null);
            }
            return;
        }
        if (mark == null) return;
        x += (TILE_WIDTH - mark.getWidth()) / 2;
        y += (TILE_HEIGHT - mark.getHeight()) / 2;
        layer.g().drawImage(mark, x, y, null);
    }
}
