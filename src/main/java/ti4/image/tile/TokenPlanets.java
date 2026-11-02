package ti4.image.tile;

import java.awt.Point;
import javax.annotation.Nullable;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;

final class TokenPlanets {

    private static final int DEFAULT_RADIUS = 55;

    private TokenPlanets() {}

    @Nullable
    static Point offset(@Nullable Point position, UnitHolder planet, Tile tile) {
        if (position == null) return null;
        if (Constants.TOKEN_PLANETS.contains(planet.getName())) {
            Point centerPosition = TokenPlanetPlacement.holderCenter(planet, tile);
            int radius = DEFAULT_RADIUS;
            if (planet instanceof Planet p) {
                radius = (int) p.getRadius();
            }
            position = new Point(position);
            position.translate(centerPosition.x - radius, centerPosition.y - radius);
        }
        return position;
    }
}
