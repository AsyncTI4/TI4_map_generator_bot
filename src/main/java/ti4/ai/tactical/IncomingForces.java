package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import ti4.ai.brain.StrategyCard;
import ti4.ai.brain.TickMemo;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.FighterWings;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.Journey;
import ti4.ai.eval.MoveBoost;
import ti4.ai.eval.MoveBudget;
import ti4.ai.eval.MoveEnablers;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.Footprint;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
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
            return ships.entrySet().stream().noneMatch(ship -> ship.getKey() != UnitType.Fighter && ship.getValue() > 0)
                    && moves.stream().noneMatch(move -> move.type() == UnitType.Fighter && move.count() > 0);
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
    private static final String SHARED_FORCES = "incoming.forces";

    private final Game game;
    private final Set<String> screened;
    private final Exclusion exclusion;
    private final IncomingForces base;
    private final Map<String, IncomingForce> forces;
    private final boolean plain;

    IncomingForces(Game game) {
        this(game, null, null);
    }

    IncomingForces(Game game, @Nullable Exclusion exclusion) {
        this(game, exclusion, exclusion == null ? null : new IncomingForces(game));
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
        this.plain = exclusion == null && screened.isEmpty();
        this.forces = plain ? TickMemo.mapFor(game, SHARED_FORCES) : new HashMap<>();
    }

    private IncomingForce remembered(String key, Function<IncomingForces, IncomingForce> compute) {
        IncomingForce found = forces.computeIfAbsent(key, ignored -> compute.apply(this));
        if (!plain) return found;
        return TickMemo.checked(SHARED_FORCES, key, found, () -> compute.apply(new IncomingForces(game)));
    }

    private static String assumptions() {
        return MoveBoost.boostedFaction() + "/" + FleetLimit.unlimitedFaction() + "/" + MovementGraph.assumesAntimass()
                + "/" + MoveEnablers.assumedKey() + "/" + DecidingSeat.id();
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
        return remembered(
                enemy.getFaction() + "#" + target.getPosition() + "#" + horizon + "#" + assumptions(),
                forcesOf -> forcesOf.scan(enemy, target, horizon, journeyFor(horizon)));
    }

    IncomingForce approaching(Player enemy, Tile target) {
        if (base != null && exclusion != null && enemy != exclusion.owner()) return base.approaching(enemy, target);
        return remembered(
                enemy.getFaction() + "#" + target.getPosition() + "#approaching#" + assumptions(),
                forcesOf -> forcesOf.scan(enemy, target, Horizon.NEXT_ROUND, Journey.approaching()));
    }

    Map<String, Integer> reach(Player enemy, Tile origin, int move) {
        return MovementGraph.distances(game, enemy, origin.getPosition(), move, screened);
    }

    IncomingForce activating(Player enemy, Tile target) {
        return remembered(
                enemy.getFaction() + "#" + target.getPosition() + "#activating#" + assumptions(),
                forcesOf -> forcesOf.gather(enemy, target, Horizon.IMMEDIATE, Journey.thisAction()));
    }

    private static Journey journeyFor(Horizon horizon) {
        return horizon == Horizon.IMMEDIATE ? Journey.thisAction() : Journey.nextRound();
    }

    private IncomingForce scan(Player enemy, Tile target, Horizon horizon, Journey journey) {
        try (Thinking.Span timing = Thinking.measure("incoming.force")) {
            if (blocked(enemy, target, horizon)) return IncomingForce.none(enemy, horizon);
            return gather(enemy, target, horizon, journey);
        }
    }

    private IncomingForce gather(Player enemy, Tile target, Horizon horizon, Journey journey) {
        if (enemy.isNeutral()) return assemble(enemy, target, horizon, Map.of());
        int supply = fleetSupply(enemy, target);
        Map<Tile, Map<UnitType, Integer>> reaching = new LinkedHashMap<>();
        // TODO: PoK Warfare + Fleet Logistics can unlock one origin; exclusions do not unblock paths
        MoveBudget.shipsWithin(
                        game,
                        enemy,
                        target,
                        journey,
                        origin -> canSetOut(enemy, origin, horizon),
                        type -> cost(enemy, type),
                        supply,
                        screened)
                .forEach((position, ships) ->
                        addShips(reaching, game.getTileByPosition(position), movingShips(enemy, ships)));
        return assemble(enemy, target, horizon, reaching);
    }

    private Map<UnitType, Integer> movingShips(Player enemy, Map<UnitType, Integer> ships) {
        return FighterWings.flies(game, enemy) ? ships : nonFighterShips(ships);
    }

    private static void addShips(
            Map<Tile, Map<UnitType, Integer>> reaching, @Nullable Tile origin, Map<UnitType, Integer> ships) {
        if (origin == null) return;
        ships.forEach((type, count) -> reaching.computeIfAbsent(origin, ignored -> new EnumMap<>(UnitType.class))
                .merge(type, count, Integer::sum));
    }

    private boolean canSetOut(Player enemy, Tile origin, Horizon horizon) {
        return !isGone(enemy, origin) && !(horizon == Horizon.IMMEDIATE && origin.hasPlayerCC(enemy));
    }

    private boolean blocked(Player enemy, Tile target, Horizon horizon) {
        if (horizon == Horizon.NEXT_ROUND) return false;
        if (enemy.isPassed()) return true;
        if (holdsUnplayedThundersEdgeWarfare(game, enemy)) return false;
        return enemy.getTacticalCC() <= 0 || target.hasPlayerCC(enemy);
    }

    private int fleetSupply(Player enemy, Tile target) {
        if (isGone(enemy, target)) return FleetLimit.ships(enemy);
        UnitHolder space = BoardView.space(target);
        int resident = FighterWings.fleetSlots(
                game,
                enemy,
                BoardView.ships(space, enemy),
                BoardView.groundForces(space, enemy),
                hasDock(enemy, target));
        return FleetLimit.ships(enemy) - resident;
    }

    private static boolean hasDock(Player enemy, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> BoardView.count(planet, enemy, UnitType.Spacedock) > 0);
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
        int[] flown = {0};
        kept.forEach((origin, arriving) -> {
            UnitHolder space = BoardView.space(origin);
            String position = origin.getPosition();
            int flying = arriving.getOrDefault(UnitType.Fighter, 0);
            flown[0] += flying;
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
            int fighters = Math.min(capacity, BoardView.count(space, enemy, UnitType.Fighter) - flying);
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
        fleetCost += flown[0] * cost(enemy, UnitType.Fighter);
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
