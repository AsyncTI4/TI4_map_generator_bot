package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.image.draw.IconHalo;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class TraitsStep implements TileStepPainter {

    static final float ICON_AREA = 9200.0f;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (HighlightGuards.isHiddenOrHyperlane(scene, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        HighlightGuards.fogOut(layer);

        for (UnitHolder uh : tile.getUnitHolders().values()) {
            if (!(uh instanceof Planet planet)) {
                continue;
            }
            String traitFile = traitFile(game, tile, planet);
            if (traitFile != null && !traitFile.isEmpty()) {
                drawTrait(layer, planet, traitFile, tile.isMecatol(game));
            }
        }
    }

    private static String traitFile(Game game, Tile tile, Planet planet) {
        List<String> traits = new ArrayList<>(planet.getPlanetType());
        if (traits.isEmpty() && StringUtils.isNotBlank(planet.getOriginalPlanetType())) {
            traits.add(planet.getOriginalPlanetType());
        }

        if (tile.isMecatol(game)) {
            return ImageResources.faction("agenda.png");
        }
        if ("faction".equalsIgnoreCase(planet.getOriginalPlanetType())) {
            return ImageResources.faction(Mapper.getPlanet(planet.getName()).getFactionHomeworld() + ".png");
        }
        if (traits.size() == 1) {
            String t = traits.getFirst();
            return ImageResources.general(
                    ("" + t.charAt(0)).toUpperCase() + t.substring(1).toLowerCase() + ".png");
        }
        if (!traits.isEmpty()) {
            String t = "";
            t += traits.contains("cultural") ? "C" : "";
            t += traits.contains("hazardous") ? "H" : "";
            t += traits.contains("industrial") ? "I" : "";
            if ("CHI".equals(t)) {
                return ImageResources.planetCard("pc_attribute_combo_CHI_big.png");
            }
            return ImageResources.planetCard("pc_attribute_combo_" + t + ".png");
        }
        return "";
    }

    private static void drawTrait(TileLayer layer, Planet planet, String traitFile, boolean mecatol) {
        BufferedImage source = ImageHelper.read(traitFile);
        float scale = (float) Math.sqrt(ICON_AREA / source.getWidth() / source.getHeight());
        int innerBorder = mecatol ? 3 : 2;
        int outerBorder = innerBorder + (mecatol ? -1 : 2);
        int padding = outerBorder + (mecatol ? 3 : 2);
        IconHalo.HaloedIcon haloed =
                IconHalo.scaledTwoTone(traitFile, source, scale, innerBorder, outerBorder, padding);
        BufferedImage icon = haloed.icon();
        IconHalo halo = haloed.halo();
        int w = icon.getWidth();
        int h = icon.getHeight();

        Point position = TokenPlanetPlacement.holderCenter(planet, layer.tile());
        position = new Point(position.x - w / 2 + TILE_PADDING, position.y - h / 2 + TILE_PADDING);
        halo.drawBehind(layer.g(), position.x, position.y);
        layer.g().drawImage(icon, position.x, position.y, null);
    }
}
