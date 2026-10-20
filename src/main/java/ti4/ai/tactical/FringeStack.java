package ti4.ai.tactical;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class FringeStack {

    public static final String OCCUPY_THE_FRINGE = "otf";
    public static final int GROUND_FORCES = 9;
    public static final int PROTECTED_FROM = 5;
    private static final int GIVEN_UP_AT = 6;

    public static boolean held(Player seat) {
        return seat.getSecretsUnscored().containsKey(OCCUPY_THE_FRINGE);
    }

    public static Optional<Planet> best(Game game, Player seat) {
        if (!held(seat)) return Optional.empty();
        return seat.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) == 0)
                .filter(planet -> BoardView.groundForces(planet, seat) > 0)
                .max(Comparator.comparingInt((Planet planet) -> BoardView.groundForces(planet, seat))
                        .thenComparing(planet -> nearHome(game, seat, planet.getName())));
    }

    public static boolean isProtected(Game game, Player seat, Planet planet) {
        return best(game, seat)
                .filter(stack -> stack.getName().equals(planet.getName()))
                .filter(stack -> BoardView.groundForces(stack, seat) >= PROTECTED_FROM)
                .filter(stack -> !givenUp(game, BoardView.groundForces(stack, seat)))
                .isPresent();
    }

    public static int kept(Game game, Player seat, Planet planet) {
        return isProtected(game, seat, planet) ? GROUND_FORCES : 0;
    }

    public static boolean nearHome(Game game, Player seat, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        Tile home = seat.getHomeSystemTile();
        if (tile == null || home == null) return false;
        if (tile == home) return true;
        Set<String> adjacent = FoWHelper.getAdjacentTiles(game, home.getPosition(), seat, false);
        return adjacent.contains(tile.getPosition());
    }

    private static boolean givenUp(Game game, int stacked) {
        return GameHorizon.endInSight(game) && stacked <= GIVEN_UP_AT;
    }
}
