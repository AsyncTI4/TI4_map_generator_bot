package ti4.generator;

import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.map.Tile;

@UtilityClass
public class PlanetHelper {

    public static String getPlanet(Tile tile, String planetName) {
        if (tile.isSpaceHolderValid(planetName)) {
            return planetName;
        }
        return tile.getUnitHolders().keySet().stream()
            .filter(id -> !Constants.SPACE.equals(planetName))
            .filter(unitHolderID -> unitHolderID.startsWith(planetName))
            .findFirst().orElse(planetName);
    }
}
