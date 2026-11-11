package ti4.image.tile;

import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import ti4.model.Mapper;
import ti4.model.PlanetLayoutModel;
import ti4.model.PlanetLayoutModel.ResInfLocation;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

final class PlanetOcclusion {

    private static final double VALUE_RADIUS = 15;
    private static final double BANNER_HEIGHT = 22;
    private static final double BANNER_GAP = 8;
    private static final double BANNER_NEAR = 12;
    private static final double BANNER_FAR = 82;
    private static final double ICON_WIDTH = 22;

    private static final Map<String, Shape> CACHE = new ConcurrentHashMap<>();

    private PlanetOcclusion() {}

    static Shape of(@Nullable TileModel tile) {
        if (tile == null || tile.getId() == null) return new Path2D.Double();
        return CACHE.computeIfAbsent(tile.getId(), _ -> build(tile.getPlanets()));
    }

    static Shape build(List<String> planetIds) {
        Path2D shapes = new Path2D.Double();
        if (planetIds == null) return shapes;
        for (String planetId : planetIds) {
            PlanetModel planet = Mapper.getPlanet(planetId);
            if (planet != null) addPlanet(shapes, planet);
        }
        return shapes;
    }

    @SuppressWarnings("deprecation")
    private static void addPlanet(Path2D shapes, PlanetModel planet) {
        Point center = planet.getPositionInTile();
        if (center == null) return;
        double x = center.x, y = center.y, r = planet.getRadius();
        shapes.append(new Ellipse2D.Double(x - r + 1, y - r + 1, 2 * r - 2, 2 * r - 2), false);
        PlanetLayoutModel layout = planet.getPlanetLayout();
        if (layout == null || layout.getResourcesLocation() == null) return;
        ResInfLocation corner = layout.getResourcesLocation();
        int dx = corner == ResInfLocation.TopLeft || corner == ResInfLocation.BottomLeft ? -1 : 1;
        int dy = corner == ResInfLocation.TopLeft || corner == ResInfLocation.TopRight ? -1 : 1;
        addValueCircle(shapes, x + dx * r * 0.5, y + dy * r * 1.1);
        addValueCircle(shapes, x + dx * r * 0.84, y + dy * r * 0.78);
        if (hasIcon(planet)) addValueCircle(shapes, x + dx * r * 1.18, y + dy * r * 0.45);
        int icons = layout.getExtraIcons() == null ? 0 : layout.getExtraIcons();
        double near = x + dx * BANNER_NEAR, far = x - dx * (BANNER_FAR + icons * ICON_WIDTH);
        double bannerY = y + dy * (r + BANNER_GAP);
        shapes.append(
                new RoundRectangle2D.Double(
                        Math.min(near, far), bannerY - BANNER_HEIGHT / 2, Math.abs(far - near), BANNER_HEIGHT, 10, 10),
                false);
    }

    private static boolean hasIcon(PlanetModel planet) {
        return planet.isLegendary()
                || planet.getTechSpecialties() != null
                        && !planet.getTechSpecialties().isEmpty();
    }

    private static void addValueCircle(Path2D shapes, double x, double y) {
        shapes.append(
                new Ellipse2D.Double(x - VALUE_RADIUS, y - VALUE_RADIUS, 2 * VALUE_RADIUS, 2 * VALUE_RADIUS), false);
    }
}
