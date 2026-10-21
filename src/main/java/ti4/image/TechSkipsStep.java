package ti4.image;

import static ti4.image.TileGeometry.TILE_PADDING;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
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
            case "biotic" -> ImageResources.general("Biotic light.png");
            case "cybernetic" -> ImageResources.general("Cybernetic light.png");
            case "propulsion" -> ImageResources.general("Propulsion_light.png");
            case "warfare" -> ImageResources.general("Warfare_light.png");
            default -> ImageResources.general("Generic_Technology.png");
        };
    }

    private static void drawSkip(TileLayer layer, Planet planet, String skip, int index, int number) {
        String file = skipFile(skip);
        BufferedImage source = ImageHelper.read(file);
        float scale = (float)
                Math.sqrt(TraitsStep.ICON_AREA / source.getWidth() / source.getHeight() / Math.sqrt(1.0f * number));
        IconHalo.HaloedIcon haloed = IconHalo.scaledTwoTone(file, source, scale, INNER_BORDER, OUTER_BORDER, PADDING);
        BufferedImage icon = haloed.icon();
        IconHalo halo = haloed.halo();
        int w = icon.getWidth();
        int h = icon.getHeight();

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
