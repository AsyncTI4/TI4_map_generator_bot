package ti4.image;

import static ti4.image.TileGeometry.TILE_PADDING;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Tile;

final class TechSkipsStep implements TileStepPainter {

    private static final int INNER_BORDER = 3;
    private static final int OUTER_BORDER = INNER_BORDER + 3;
    private static final int PADDING = OUTER_BORDER + 2;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (HighlightGuards.isFogOrHyperlane(game, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        if (tile.getPlanetUnitHolders().isEmpty()) {
            HighlightGuards.fogOut(layer);
            return;
        }
        boolean anySkips = false;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            List<String> skips = planet.getTechSpecialities();
            if (skips.isEmpty()) {
                continue;
            }
            anySkips = true;
            int count = 0;
            for (String skip : skips) {
                drawSkip(layer, planet, skip, count, skips.size());
                count++;
            }
        }
        if (!anySkips) {
            HighlightGuards.fogOut(layer);
        }
    }

    private static String skipFile(String skip) {
        return switch (skip.toLowerCase()) {
            case "biotic" -> ResourceHelper.getInstance().getGeneralFile("Biotic light.png");
            case "cybernetic" -> ResourceHelper.getInstance().getGeneralFile("Cybernetic light.png");
            case "propulsion" -> ResourceHelper.getInstance().getGeneralFile("Propulsion_light.png");
            case "warfare" -> ResourceHelper.getInstance().getGeneralFile("Warfare_light.png");
            default -> ResourceHelper.getInstance().getGeneralFile("Generic_Technology.png");
        };
    }

    private static void drawSkip(TileLayer layer, Planet planet, String skip, int index, int number) {
        BufferedImage icon = ImageHelper.read(skipFile(skip));
        icon = ImageHelper.scale(icon, (float)
                Math.sqrt(TraitsStep.ICON_AREA / icon.getWidth() / icon.getHeight() / Math.sqrt(1.0f * number)));
        int w = icon.getWidth();
        int h = icon.getHeight();
        IconHalo halo = IconHalo.twoTone(icon, INNER_BORDER, OUTER_BORDER, PADDING);

        Point position = planet.getHolderCenterPosition(layer.tile());
        if (number > 1) {
            int offset = -20 + index * 40 / (number - 1);
            position.translate(offset, offset);
        }
        position = new Point(position.x - w / 2 + TILE_PADDING, position.y - h / 2 + TILE_PADDING);
        halo.drawBehind(layer.g(), position.x, position.y);
        layer.g().drawImage(icon, position.x, position.y, null);
    }
}
