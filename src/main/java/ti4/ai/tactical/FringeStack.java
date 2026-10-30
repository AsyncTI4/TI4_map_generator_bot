package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
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
    public static final int PROTECTED_FROM = 3;
    private static final int ANCHOR_SLACK = 2;
    private static final int IN_DOCK_SYSTEM = 0;
    private static final int NEXT_TO_DOCK_SYSTEM = 1;
    private static final int ELSEWHERE = 2;

    public static boolean held(Player seat) {
        return seat.getSecretsUnscored().containsKey(OCCUPY_THE_FRINGE);
    }

    public static Optional<Planet> best(Game game, Player seat) {
        if (!held(seat)) return Optional.empty();
        List<Planet> candidates = docklessPlanets(game, seat);
        int largest = candidates.stream()
                .mapToInt(planet -> BoardView.groundForces(planet, seat))
                .max()
                .orElse(0);
        if (largest == 0) return Optional.empty();
        List<Tile> dockSystems = dockSystems(game, seat);
        return candidates.stream()
                .filter(planet -> BoardView.groundForces(planet, seat) >= largest - ANCHOR_SLACK)
                .min(Comparator.comparingInt((Planet planet) -> distanceToDocks(game, seat, dockSystems, planet))
                        .thenComparing(Comparator.comparingInt((Planet planet) -> BoardView.groundForces(planet, seat))
                                .reversed())
                        .thenComparing(planet -> !nearHome(game, seat, planet.getName())));
    }

    static Optional<Planet> fallback(Game game, Player seat) {
        if (!held(seat)) return Optional.empty();
        List<Tile> dockSystems = dockSystems(game, seat);
        return docklessPlanets(game, seat).stream()
                .min(Comparator.comparingInt((Planet planet) -> distanceToDocks(game, seat, dockSystems, planet))
                        .thenComparing(planet -> !nearHome(game, seat, planet.getName())));
    }

    private static List<Planet> docklessPlanets(Game game, Player seat) {
        return seat.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) == 0)
                .toList();
    }

    public static boolean inOrNextTo(Game game, Player seat, Tile dockSystem, Planet anchor) {
        Tile tile = game.getTileFromPlanet(anchor.getName());
        if (tile == null) return false;
        if (tile == dockSystem) return true;
        return FoWHelper.getAdjacentTiles(game, dockSystem.getPosition(), seat, false)
                .contains(tile.getPosition());
    }

    public static boolean isProtected(Game game, Player seat, Planet planet) {
        return GroundCampaign.of(game, seat).protects(planet);
    }

    public static boolean nearHome(Game game, Player seat, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        Tile home = seat.getHomeSystemTile();
        if (tile == null || home == null) return false;
        if (tile == home) return true;
        Set<String> adjacent = FoWHelper.getAdjacentTiles(game, home.getPosition(), seat, false);
        return adjacent.contains(tile.getPosition());
    }

    static List<Tile> dockSystems(Game game, Player seat) {
        return seat.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .filter(holder -> BoardView.count(holder, seat, UnitType.Spacedock) > 0)
                .map(holder -> game.getTileFromPlanet(holder.getName()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static int distanceToDocks(Game game, Player seat, List<Tile> dockSystems, Planet planet) {
        if (dockSystems.stream().anyMatch(dock -> game.getTileFromPlanet(planet.getName()) == dock)) {
            return IN_DOCK_SYSTEM;
        }
        boolean adjacent = dockSystems.stream().anyMatch(dock -> inOrNextTo(game, seat, dock, planet));
        return adjacent ? NEXT_TO_DOCK_SYSTEM : ELSEWHERE;
    }
}
