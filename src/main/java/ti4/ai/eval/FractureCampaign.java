package ti4.ai.eval;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class FractureCampaign {

    public static final int DOCK_RANGE = 2;
    public static final int DREADNOUGHTS = 3;
    public static final int CARRIERS = 1;
    public static final int TAKING_SHIPS = DREADNOUGHTS + CARRIERS;
    public static final int INFANTRY = 6;
    public static final double FOREIGN_ROUTE_SHARE = 0.5;
    public static final int GATHER_ACTIONS = 2;
    public static final int GATHER_FLEET_MAX = 6;

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

    public static boolean takesStyx(Player seat) {
        return AiExperiments.enabled(seat, AiExperiments.Experiment.STYX_TAKE);
    }

    public static int fleetShips(Game game, Player seat) {
        if (!takesStyx(seat)) return TAKING_SHIPS;
        return Math.max(TAKING_SHIPS, strongestRivalFleet(game, seat));
    }

    public static int gatheringFleet(Game game, Player seat) {
        Optional<Tile> styx = styxTile(game);
        if (!takesStyx(seat) || styx.isEmpty() || !styxToTake(game, seat)) return 0;
        Map<String, Map<UnitType, Integer>> gathered = MoveBudget.shipsWithin(
                game,
                seat,
                styx.get(),
                Journey.rounds(GATHER_ACTIONS),
                tile -> true,
                FractureCampaign::countsTowardFleet,
                GATHER_FLEET_MAX);
        int ships = gathered.values().stream()
                .flatMap(stack -> stack.entrySet().stream())
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(Map.Entry::getValue)
                .sum();
        return Math.min(GATHER_FLEET_MAX, ships);
    }

    private static double countsTowardFleet(UnitType type) {
        return BoardView.MOVING_SHIPS.contains(type) ? 1 : 0;
    }

    private static Optional<Tile> styxTile(Game game) {
        return FracturePrize.tiles(game).stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream().anyMatch(FracturePrize::isStyx))
                .findFirst();
    }

    public static int dreadnoughts(Game game, Player seat) {
        return fleetShips(game, seat) - CARRIERS;
    }

    public static int infantry(Game game, Player seat) {
        if (!takesStyx(seat)) return INFANTRY;
        int room = dreadnoughts(game, seat) * BoardView.capacity(seat, UnitType.Dreadnought)
                + CARRIERS * BoardView.capacity(seat, UnitType.Carrier);
        return Math.max(INFANTRY, room);
    }

    private static int strongestRivalFleet(Game game, Player seat) {
        int strongest = 0;
        for (Player rival : game.getRealPlayers()) {
            if (rival == seat) continue;
            for (Tile tile : game.getTileMap().values()) {
                strongest = Math.max(strongest, BoardView.nonFighterShips(BoardView.space(tile), rival));
            }
        }
        return strongest;
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
        Map<String, Integer> reach = MovementGraph.distances(game, seat, tile.getPosition(), DOCK_RANGE);
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
