package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.Footprint;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.StrategyCardModel;
import ti4.model.UnitModel;

final class IncomingForces {

    enum Horizon {
        IMMEDIATE,
        NEXT_ROUND
    }

    record IncomingForce(
            Player enemy,
            Horizon horizon,
            Map<UnitType, Integer> ships,
            Map<UnitType, Integer> damaged,
            Map<UnitType, Integer> ground,
            List<Footprint.Move> moves,
            double fleetCost,
            double groundCost) {

        static IncomingForce none(Player enemy, Horizon horizon) {
            return new IncomingForce(enemy, horizon, Map.of(), Map.of(), Map.of(), List.of(), 0, 0);
        }

        boolean isEmpty() {
            return ships.entrySet().stream()
                    .noneMatch(ship -> ship.getKey() != UnitType.Fighter && ship.getValue() > 0);
        }

        boolean carriesGround() {
            return ground.values().stream().anyMatch(count -> count > 0);
        }

        String key() {
            return enemy.getFaction() + "|" + describe(ships) + "|" + describe(damaged) + "|" + describe(ground) + "|"
                    + moves;
        }

        private static String describe(Map<UnitType, Integer> units) {
            Map<String, Integer> sorted = new TreeMap<>();
            units.forEach((type, count) -> {
                if (count > 0) sorted.put(type.name(), count);
            });
            return sorted.toString();
        }
    }

    record Exclusion(Player owner, Tile tile) {}

    private static final String THUNDERS_EDGE_WARFARE = "te6warfare";
    private static final int ONE_MOVE = 1;
    private static final int TWO_MOVES = 2;

    private record Slot(Tile origin, UnitType type, double cost) {}

    private final Game game;
    private final Set<String> screened;
    private final Exclusion exclusion;
    private final IncomingForces base;
    private final Map<String, Map<String, Integer>> reaches;
    private final Map<String, IncomingForce> forces = new HashMap<>();

    IncomingForces(Game game) {
        this(game, null, null);
    }

    IncomingForces(Game game, @Nullable Exclusion exclusion) {
        this(game, exclusion, null);
    }

    IncomingForces(Game game, @Nullable Exclusion exclusion, @Nullable IncomingForces base) {
        this(game, exclusion, base, Set.of());
    }

    static IncomingForces screened(Game game, Set<String> screened) {
        return new IncomingForces(game, null, null, screened);
    }

    private IncomingForces(
            Game game, @Nullable Exclusion exclusion, @Nullable IncomingForces base, Set<String> screened) {
        this.game = game;
        this.exclusion = exclusion;
        this.base = base;
        this.screened = screened;
        this.reaches = base == null ? new HashMap<>() : base.reaches;
    }

    private boolean isGone(Player enemy, Tile tile) {
        return exclusion != null && exclusion.owner() == enemy && exclusion.tile() == tile;
    }

    static boolean holdsUnplayedWarfare(Game game, Player player) {
        return unplayedWarfare(game, player).findAny().isPresent();
    }

    static boolean holdsUnplayedThundersEdgeWarfare(Game game, Player player) {
        return unplayedWarfare(game, player).anyMatch(initiative -> isThundersEdgeCard(game, initiative));
    }

    static boolean holdsUnplayedPokWarfare(Game game, Player player) {
        return unplayedWarfare(game, player).anyMatch(initiative -> !isThundersEdgeCard(game, initiative));
    }

    private static Stream<Integer> unplayedWarfare(Game game, Player player) {
        return player.getSCs().stream()
                .filter(initiative -> StrategyCard.of(game, initiative) == StrategyCard.WARFARE)
                .filter(initiative -> !game.getPlayedSCs().contains(initiative));
    }

    private static boolean isThundersEdgeCard(Game game, int initiative) {
        return game.getStrategyCardModelByInitiative(initiative)
                .map(StrategyCardModel::getBotSCAutomationID)
                .filter(THUNDERS_EDGE_WARFARE::equalsIgnoreCase)
                .isPresent();
    }

    IncomingForce of(Player enemy, Tile target, Horizon horizon) {
        if (base != null && exclusion != null && enemy != exclusion.owner()) return base.of(enemy, target, horizon);
        return forces.computeIfAbsent(
                enemy.getFaction() + "#" + target.getPosition() + "#" + horizon,
                key -> scan(enemy, target, horizon, ONE_MOVE));
    }

    IncomingForce approaching(Player enemy, Tile target) {
        if (base != null && exclusion != null && enemy != exclusion.owner()) return base.approaching(enemy, target);
        return forces.computeIfAbsent(
                enemy.getFaction() + "#" + target.getPosition() + "#approaching",
                key -> scan(enemy, target, Horizon.NEXT_ROUND, TWO_MOVES));
    }

    Map<String, Integer> reach(Player enemy, Tile origin, int move) {
        return reaches.computeIfAbsent(
                enemy.getFaction() + "#" + origin.getPosition() + "#" + move,
                key -> MovementGraph.reach(game, enemy, origin.getPosition(), move, screened));
    }

    IncomingForce activating(Player enemy, Tile target) {
        return forces.computeIfAbsent(
                enemy.getFaction() + "#" + target.getPosition() + "#activating",
                key -> gather(enemy, target, Horizon.IMMEDIATE, ONE_MOVE));
    }

    private IncomingForce scan(Player enemy, Tile target, Horizon horizon, int moves) {
        if (blocked(enemy, target, horizon)) return IncomingForce.none(enemy, horizon);
        return gather(enemy, target, horizon, moves);
    }

    private IncomingForce gather(Player enemy, Tile target, Horizon horizon, int moves) {
        if (enemy.isNeutral()) return assemble(enemy, target, horizon, Map.of());
        Map<Tile, Map<UnitType, Integer>> reaching = new LinkedHashMap<>();
        Slot gravityDriveShip = null;
        // TODO: PoK Warfare + Fleet Logistics can unlock one origin; exclusions do not unblock paths
        for (Tile origin : game.getTileMap().values()) {
            if (origin == target
                    || isGone(enemy, origin)
                    || (horizon == Horizon.IMMEDIATE && origin.hasPlayerCC(enemy))) {
                continue;
            }
            Map<UnitType, Integer> arriving = new EnumMap<>(UnitType.class);
            for (Map.Entry<UnitType, Integer> ship : nonFighterShips(BoardView.ships(BoardView.space(origin), enemy))
                    .entrySet()) {
                int move = BoardView.moveValue(enemy, ship.getKey()) * moves;
                if (reaches(enemy, origin, target, move)) {
                    arriving.put(ship.getKey(), ship.getValue());
                } else if (enemy.hasTech("gd") && reaches(enemy, origin, target, move + moves)) {
                    Slot candidate = new Slot(origin, ship.getKey(), cost(enemy, ship.getKey()));
                    if (gravityDriveShip == null || candidate.cost() > gravityDriveShip.cost()) {
                        gravityDriveShip = candidate;
                    }
                }
            }
            if (!arriving.isEmpty()) reaching.put(origin, arriving);
        }
        if (gravityDriveShip != null) {
            reaching.computeIfAbsent(gravityDriveShip.origin(), origin -> new EnumMap<>(UnitType.class))
                    .merge(gravityDriveShip.type(), 1, Integer::sum);
        }
        return assemble(enemy, target, horizon, withinFleetSupply(enemy, target, reaching));
    }

    private boolean blocked(Player enemy, Tile target, Horizon horizon) {
        if (horizon == Horizon.NEXT_ROUND) return false;
        if (enemy.isPassed()) return true;
        if (holdsUnplayedThundersEdgeWarfare(game, enemy)) return false;
        return enemy.getTacticalCC() <= 0 || target.hasPlayerCC(enemy);
    }

    private boolean reaches(Player enemy, Tile origin, Tile target, int move) {
        if (move <= 0) return false;
        if (origin.isGravityRift(game, enemy)) return reachesFromRift(enemy, origin, target, move);
        return reach(enemy, origin, move).containsKey(target.getPosition());
    }

    // TODO: rift survival roll not modelled
    private boolean reachesFromRift(Player enemy, Tile rift, Tile target, int move) {
        for (String position : FoWHelper.getAdjacentTiles(game, rift.getPosition(), enemy, false)) {
            Tile neighbour = game.getTileByPosition(position);
            if (neighbour == null || neighbour == rift || !MovementGraph.canEnter(game, enemy, neighbour)) continue;
            if (BoardView.hasEnemyShips(game, enemy, neighbour)) {
                if (neighbour == target) return true;
            } else if (reach(enemy, neighbour, move).containsKey(target.getPosition())) {
                return true;
            }
        }
        return false;
    }

    private Map<Tile, Map<UnitType, Integer>> withinFleetSupply(
            Player enemy, Tile target, Map<Tile, Map<UnitType, Integer>> reaching) {
        int resident = isGone(enemy, target) ? 0 : BoardView.nonFighterShips(BoardView.space(target), enemy);
        int supply = FleetLimit.ships(enemy) - resident;
        List<Slot> slots = new ArrayList<>();
        reaching.forEach((origin, ships) -> ships.forEach((type, count) -> {
            for (int i = 0; i < count; i++) slots.add(new Slot(origin, type, cost(enemy, type)));
        }));
        slots.sort(Comparator.comparingDouble(Slot::cost)
                .reversed()
                .thenComparing(slot -> slot.origin().getPosition())
                .thenComparing(slot -> slot.type().name()));
        Map<Tile, Map<UnitType, Integer>> kept = new LinkedHashMap<>();
        for (Slot slot : slots.subList(0, Math.max(0, Math.min(supply, slots.size())))) {
            kept.computeIfAbsent(slot.origin(), origin -> new EnumMap<>(UnitType.class))
                    .merge(slot.type(), 1, Integer::sum);
        }
        return kept;
    }

    // TODO: the enemy is assumed to commit everything that reaches; ground forces survive the space combat
    private IncomingForce assemble(Player enemy, Tile target, Horizon horizon, Map<Tile, Map<UnitType, Integer>> kept) {
        Map<UnitType, Integer> ships = new EnumMap<>(UnitType.class);
        Map<UnitType, Integer> damaged = new EnumMap<>(UnitType.class);
        Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
        List<Footprint.Move> moves = new ArrayList<>();
        UnitHolder targetSpace = BoardView.space(target);
        if (!isGone(enemy, target)) {
            BoardView.ships(targetSpace, enemy).forEach((type, count) -> ships.merge(type, count, Integer::sum));
            BattleMath.damagedAmong(targetSpace, enemy, ships)
                    .forEach((type, count) -> damaged.merge(type, count, Integer::sum));
            for (UnitType type : BoardView.GROUND_FORCES) {
                int waiting = BoardView.count(targetSpace, enemy, type);
                if (waiting > 0) ground.merge(type, waiting, Integer::sum);
            }
        }
        kept.forEach((origin, arriving) -> {
            UnitHolder space = BoardView.space(origin);
            String position = origin.getPosition();
            int capacity = 0;
            for (Map.Entry<UnitType, Integer> ship : arriving.entrySet()) {
                ships.merge(ship.getKey(), ship.getValue(), Integer::sum);
                damaged.merge(
                        ship.getKey(),
                        Math.min(ship.getValue(), BoardView.damaged(space, enemy, ship.getKey())),
                        Integer::sum);
                moves.add(new Footprint.Move(position, ship.getKey(), ship.getValue()));
                capacity += ship.getValue() * BoardView.capacity(enemy, ship.getKey());
            }
            for (UnitType type : List.of(UnitType.Mech, UnitType.Infantry)) {
                int carried = Math.min(capacity, groundForcesAt(enemy, origin, type));
                if (carried <= 0) continue;
                ground.merge(type, carried, Integer::sum);
                moves.add(new Footprint.Move(position, type, carried));
                capacity -= carried;
            }
            int fighters = Math.min(capacity, BoardView.count(space, enemy, UnitType.Fighter));
            if (fighters > 0) {
                ships.merge(UnitType.Fighter, fighters, Integer::sum);
                moves.add(new Footprint.Move(position, UnitType.Fighter, fighters));
            }
        });
        damaged.values().removeIf(count -> count <= 0);
        double fleetCost = 0;
        for (Map.Entry<UnitType, Integer> ship : ships.entrySet()) {
            if (ship.getKey() != UnitType.Fighter) fleetCost += ship.getValue() * cost(enemy, ship.getKey());
        }
        double groundCost = 0;
        for (Map.Entry<UnitType, Integer> force : ground.entrySet()) {
            groundCost += force.getValue() * cost(enemy, force.getKey());
        }
        IncomingForce force = new IncomingForce(enemy, horizon, ships, damaged, ground, moves, fleetCost, groundCost);
        return force.isEmpty() ? IncomingForce.none(enemy, horizon) : force;
    }

    private static int groundForcesAt(Player enemy, Tile origin, UnitType type) {
        int total = BoardView.count(BoardView.space(origin), enemy, type);
        for (Planet planet : origin.getPlanetUnitHolders()) {
            if (enemy.getPlanets().contains(planet.getName())) total += BoardView.count(planet, enemy, type);
        }
        return total;
    }

    private static Map<UnitType, Integer> nonFighterShips(Map<UnitType, Integer> ships) {
        Map<UnitType, Integer> withoutFighters = new EnumMap<>(UnitType.class);
        ships.forEach((type, count) -> {
            if (type != UnitType.Fighter) withoutFighters.put(type, count);
        });
        return withoutFighters;
    }

    static double cost(Player player, UnitType type) {
        return BoardView.model(player, type).map(UnitModel::getCost).orElse(0f);
    }
}
