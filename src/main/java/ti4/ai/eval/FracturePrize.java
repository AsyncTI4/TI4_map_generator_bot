package ti4.ai.eval;

import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;

@UtilityClass
public class FracturePrize {

    public static final String STYX = "styx";
    private static final double STYX_INTEREST = 1.0;
    private static final double PLANET_INTEREST = 0.5;
    private static final String RELIC_TOKEN = "token_relictoken.png";
    private static final Set<String> PLANETS = Set.of("cocytus", STYX, "lethe", "phlegethon");

    public static boolean isFracturePlanet(Planet planet) {
        return PLANETS.contains(planet.getName());
    }

    public static boolean isStyx(Planet planet) {
        return STYX.equals(planet.getName());
    }

    public static boolean holdsStyx(Tile tile, Player owner) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> isStyx(planet) && owner.getPlanets().contains(planet.getName()));
    }

    public static double relic(Game game, Player seat, Planet planet) {
        return planet.getTokenList().contains(RELIC_TOKEN) ? RelicValue.draw(game, seat) : 0;
    }

    public static double point(Planet planet) {
        return isStyx(planet) ? ObjectiveValue.VICTORY_POINT_VALUE : 0;
    }

    public static double gain(Game game, Player seat, Planet planet) {
        return relic(game, seat, planet) + point(planet);
    }

    public static boolean hasIngress(Tile tile) {
        return tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS);
    }

    public static double interest(Game game, Player seat) {
        double interest = 0;
        for (Tile tile : tiles(game)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!isFracturePlanet(planet) || seat.getPlanets().contains(planet.getName())) continue;
                if (isStyx(planet)) return STYX_INTEREST;
                if (BoardView.controller(game, planet.getName()) == null) interest = PLANET_INTEREST;
            }
        }
        return interest;
    }

    public static List<Tile> tiles(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream().anyMatch(FracturePrize::isFracturePlanet))
                .toList();
    }
}
