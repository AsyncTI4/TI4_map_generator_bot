package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class GroundDrops {

    public static final int INFANTRY = 2;
    public static final int MECH_RESOURCES = 3;
    private static final double INFANTRY_FLOOR = 0.3;
    private static final double NEEDED_INFANTRY_VALUE = 0.5;
    private static final double INVASION_INFANTRY_VALUE = 1.0;
    private static final double SECONDARY_SHARE = 0.25;

    public record Drop(String planet, double infantry, double mech) {}

    public static Optional<Drop> best(Game game, Player seat, Optional<TacticalPlan> plan) {
        return drops(game, seat, plan).stream().max(Comparator.comparingDouble(Drop::infantry));
    }

    public static OptionalDouble need(Game game, Player seat) {
        return drops(game, seat, Optional.empty()).stream()
                .mapToDouble(drop -> drop.infantry() - INFANTRY_FLOOR * INFANTRY)
                .max();
    }

    public static int spareShortfall(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        if (home == null) return 0;
        return Math.max(
                0, ProductionPlanner.WANTED_SPARE_INFANTRY - TacticalPlanner.movableGroundForces(game, seat, home));
    }

    public static Optional<String> bestPlanetIn(Game game, Player seat, Tile tile, Optional<TacticalPlan> plan) {
        List<String> inTile =
                tile.getPlanetUnitHolders().stream().map(Planet::getName).toList();
        return drops(game, seat, plan).stream()
                .filter(drop -> inTile.contains(drop.planet()))
                .max(Comparator.comparingDouble(Drop::infantry))
                .map(Drop::planet);
    }

    public static List<Drop> drops(Game game, Player seat, Optional<TacticalPlan> plan) {
        if (!infantryAvailable(game, seat)) return List.of();
        DefenseOutlook defense = new DefenseOutlook(game, seat);
        boolean mechAvailable = mechAvailable(game, seat);
        List<Drop> drops = new ArrayList<>();
        for (String name : seat.getPlanetsAllianceMode()) {
            if (game.getUnitHolderFromPlanet(name) instanceof Planet planet && placeable(game, planet)) {
                Tile tile = game.getTileFromPlanet(name);
                if (tile != null) drops.add(drop(game, seat, defense, plan, tile, planet, mechAvailable));
            }
        }
        return drops;
    }

    private static Drop drop(
            Game game,
            Player seat,
            DefenseOutlook defense,
            Optional<TacticalPlan> plan,
            Tile tile,
            Planet planet,
            boolean mechAvailable) {
        double holding = holding(defense, tile, planet.getName(), 0, UnitType.Infantry, INFANTRY);
        double staging = staging(game, seat, tile, defense, plan);
        double objective = defense.campaign().fringeDropValue(planet, INFANTRY);
        double best = Math.max(holding, Math.max(staging, objective));
        double rest = holding + staging + objective - best;
        double infantry = best + SECONDARY_SHARE * rest + INFANTRY_FLOOR * INFANTRY;
        double mech = mechAvailable ? mechValue(defense, tile, planet) : 0;
        return new Drop(planet.getName(), infantry, mech);
    }

    private static double mechValue(DefenseOutlook defense, Tile tile, Planet planet) {
        double holding = holding(defense, tile, planet.getName(), INFANTRY, UnitType.Mech, 1);
        return holding + defense.campaign().mechDropValue(planet);
    }

    private static double holding(
            DefenseOutlook defense, Tile tile, String planet, int infantryAlready, UnitType type, int count) {
        if (!defense.isGuarded(tile)) return 0;
        Defenders now = defense.defendersAt(tile);
        if (infantryAlready > 0) now = now.plusGround(planet, UnitType.Infantry, infantryAlready);
        Defenders after = now.plusGround(planet, type, count);
        return Math.max(0, defense.cost(tile, now) - defense.cost(tile, after));
    }

    private static double staging(
            Game game, Player seat, Tile tile, DefenseOutlook defense, Optional<TacticalPlan> plan) {
        if (BoardView.hasEnemyShips(game, seat, tile)) return 0;
        int room = transportRoom(seat, tile);
        if (room <= 0) return 0;
        int shortfall = Math.max(0, room - TacticalPlanner.movableGroundForces(game, seat, tile, defense));
        boolean launchesInvasion =
                plan.filter(found -> launchesFrom(found, tile)).isPresent();
        double each = launchesInvasion ? INVASION_INFANTRY_VALUE : NEEDED_INFANTRY_VALUE;
        return Math.min(INFANTRY, shortfall) * each;
    }

    private static boolean launchesFrom(TacticalPlan plan, Tile tile) {
        boolean invades = plan.kind() == Kind.EXPAND || plan.kind() == Kind.ATTACK;
        return invades && plan.moves().stream().anyMatch(move -> move.origin().equals(tile.getPosition()));
    }

    private static int transportRoom(Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        int capacity = 0;
        for (UnitType type : BoardView.MOVING_SHIPS) {
            capacity += BoardView.count(space, seat, type) * BoardView.capacity(seat, type);
        }
        int carried = BoardView.count(space, seat, UnitType.Fighter) + BoardView.groundForces(space, seat);
        return capacity - carried;
    }

    private static boolean placeable(Game game, Planet planet) {
        boolean demilitarized = planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
        return !demilitarized && !planet.isSpaceStation(game);
    }

    private static boolean infantryAvailable(Game game, Player seat) {
        UnitModel infantry = seat.getUnitByType(UnitType.Infantry);
        if (infantry == null) return false;
        int cap = seat.getUnitCap(infantry.getAsyncId());
        return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, infantry.getAsyncId()) + INFANTRY <= cap;
    }

    public static boolean mechAvailable(Game game, Player seat) {
        UnitModel mech = seat.getUnitByType(UnitType.Mech);
        if (mech == null || game.isBaseGameMode()) return false;
        int cap = seat.getUnitCap(mech.getAsyncId());
        return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, mech.getAsyncId()) + 1 <= cap;
    }
}
