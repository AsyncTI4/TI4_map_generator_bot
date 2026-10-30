package ti4.ai.eval;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class FractureCampaign {

    public static final int DOCK_RANGE = 2;
    public static final int FLEET_SHIPS = 3;
    public static final int DREADNOUGHTS = 3;
    public static final int CARRIERS = 1;
    public static final int INFANTRY = 6;
    public static final double FOREIGN_ROUTE_SHARE = 0.5;

    public static Optional<Tile> dock(Game game, Player seat) {
        if (!styxToTake(game, seat)) return Optional.empty();
        Tile best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Tile tile : game.getTileMap().values()) {
            if (!hasDock(seat, tile)) continue;
            int distance = distanceToIngress(game, seat, tile);
            if (distance < bestDistance) {
                best = tile;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static double routeShare(Game game, Player seat) {
        return routeShare(game, Territory.of(game, seat));
    }

    public static double routeShare(Game game, Territory territory) {
        boolean ownIngress = game.getTileMap().values().stream()
                .filter(FracturePrize::hasIngress)
                .map(territory::zone)
                .anyMatch(zone ->
                        zone == Territory.Zone.HOME || zone == Territory.Zone.LANE || zone == Territory.Zone.SLICE);
        return ownIngress ? 1 : FOREIGN_ROUTE_SHARE;
    }

    public static boolean isDock(Game game, Player seat, Tile tile) {
        return dock(game, seat).filter(found -> found == tile).isPresent();
    }

    public static boolean styxToTake(Game game, Player seat) {
        return FracturePrize.tiles(game).stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(FracturePrize::isStyx)
                .map(Planet::getName)
                .anyMatch(styx -> !seat.getPlanets().contains(styx));
    }

    private static int distanceToIngress(Game game, Player seat, Tile tile) {
        Map<String, Integer> reach = MovementGraph.reach(game, seat, tile.getPosition(), DOCK_RANGE);
        return reach.entrySet().stream()
                .filter(entry -> {
                    Tile reached = game.getTileByPosition(entry.getKey());
                    return reached != null && FracturePrize.hasIngress(reached);
                })
                .mapToInt(Map.Entry::getValue)
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private static boolean hasDock(Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0);
    }
}
