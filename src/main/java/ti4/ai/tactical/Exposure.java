package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

final class Exposure {

    private static final double CAUGHT_IN_RETREAT = 0.5;

    private record Unit(UnitType type, double cost, int capacity) {}

    private final Game game;
    private final Player seat;
    private final DefenseOutlook defense;
    private final double extraActionCost;
    private final Map<String, Double> losses = new HashMap<>();

    Exposure(Game game, Player seat, DefenseOutlook defense) {
        this(game, seat, defense, 0);
    }

    Exposure(Game game, Player seat, DefenseOutlook defense, double extraActionCost) {
        this.game = game;
        this.seat = seat;
        this.defense = defense;
        this.extraActionCost = extraActionCost;
    }

    double added(Tile tile, Defenders after, List<UnitMove> moves) {
        return Math.max(0, expectedLoss(tile, after, moves) - expectedLoss(tile, defense.defendersAt(tile), List.of()));
    }

    double expectedLoss(Tile tile, Defenders group, List<UnitMove> moves) {
        if (group.ships().isEmpty()) return 0;
        String key = tile.getPosition() + "|" + group.shipsKey() + "|"
                + group.groundInSpace().key() + "|" + departures(moves);
        Double known = losses.get(key);
        if (known != null) return known;
        double worst = 0;
        for (Player enemy : game.getRealPlayers()) {
            if (enemy == seat) continue;
            for (Horizon horizon : Horizon.values()) {
                worst = Math.max(worst, defense.weight(enemy, horizon) * lossFrom(enemy, tile, horizon, group, moves));
            }
        }
        losses.put(key, worst);
        return worst;
    }

    private static String departures(List<UnitMove> moves) {
        return moves.stream()
                .map(move -> move.origin() + ":" + move.holder() + ":" + move.type() + ":" + move.count())
                .sorted()
                .toList()
                .toString();
    }

    private double lossFrom(Player enemy, Tile tile, Horizon horizon, Defenders group, List<UnitMove> moves) {
        IncomingForce force = defense.force(enemy, tile, horizon);
        if (force.isEmpty()) return 0;
        double win = defense.chances(enemy, tile, horizon, group).space();
        if (win <= 0) return 0;
        double lossIfAttacked = win * valueAtRisk(group);
        if (canRetreat(enemy, horizon, tile, group, moves)) {
            lossIfAttacked = Math.min(lossIfAttacked, firstRoundLoss(tile, force, group));
        }
        double attackerLoss =
                (1 - win) * force.fleetCost() + win * (1 - win) * Math.min(force.fleetCost(), group.shipCost(seat));
        double netGain = lossIfAttacked - attackerLoss - extraActionCost;
        return defense.attackLikelihood(enemy, tile, horizon, netGain) * lossIfAttacked;
    }

    private boolean canRetreat(Player enemy, Horizon horizon, Tile tile, Defenders group, List<UnitMove> moves) {
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .filter(destination -> destination != null && destination != tile)
                .anyMatch(destination -> safeRetreat(enemy, horizon, destination, group, moves));
    }

    private boolean safeRetreat(
            Player enemy, Horizon horizon, Tile destination, Defenders group, List<UnitMove> moves) {
        Defenders left = defense.defendersAt(destination).minus(movesFrom(destination, moves));
        if (!RetreatRules.canRetreatTo(game, seat, destination, holdsAfterMove(destination, left))) return false;
        IncomingForce pursuit = defense.force(enemy, destination, horizon);
        if (pursuit.isEmpty()) return true;
        return defense.chances(enemy, destination, horizon, left.joinedInSpaceBy(group))
                        .space()
                < CAUGHT_IN_RETREAT;
    }

    private boolean holdsAfterMove(Tile destination, Defenders left) {
        return !left.isEmpty()
                || destination.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> seat.getPlanets().contains(planet.getName()));
    }

    private static List<UnitMove> movesFrom(Tile origin, List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> move.origin().equals(origin.getPosition()))
                .toList();
    }

    private double valueAtRisk(Defenders group) {
        return costOf(group.ships().units()) + costOf(group.groundInSpace().units());
    }

    private double costOf(Map<UnitType, Integer> units) {
        double cost = 0;
        for (Map.Entry<UnitType, Integer> unit : units.entrySet()) {
            cost += unit.getValue() * IncomingForces.cost(seat, unit.getKey());
        }
        return cost;
    }

    // TODO: anti-fighter barrage and space cannon before the first round are not counted against the escorts
    private double firstRoundLoss(Tile tile, IncomingForce force, Defenders group) {
        List<Combatant> attackers = CombatForces.combatants(
                game,
                tile,
                BoardView.space(tile),
                new Side(force.enemy(), force.ships()),
                new Side(seat, group.ships().units()),
                force.damaged());
        double[] hits = hitDistribution(attackers);
        double expected = 0;
        for (int count = 0; count < hits.length; count++) {
            if (hits[count] > 0) expected += hits[count] * lossAfter(group, count);
        }
        return expected;
    }

    private static double[] hitDistribution(List<Combatant> attackers) {
        double[] distribution = {1};
        for (Combatant unit : attackers) {
            double hit = BattleMath.hitChance(unit.hitsOn());
            for (int die = 0; die < unit.dice(); die++) {
                double[] next = new double[distribution.length + 1];
                for (int count = 0; count < distribution.length; count++) {
                    next[count] += distribution[count] * (1 - hit);
                    next[count + 1] += distribution[count] * hit;
                }
                distribution = next;
            }
        }
        return distribution;
    }

    private double lossAfter(Defenders group, int hits) {
        int left = hits - sustainable(group);
        List<Unit> units = unitsCheapestFirst(group);
        double lost = 0;
        int killed = Math.min(Math.max(0, left), units.size());
        for (Unit unit : units.subList(0, killed)) lost += unit.cost();
        List<Unit> surviving = units.subList(killed, units.size());
        int room = surviving.stream().mapToInt(Unit::capacity).sum();
        int fighters = (int) surviving.stream()
                .filter(unit -> unit.type() == UnitType.Fighter)
                .count();
        for (Map.Entry<UnitType, Integer> ground : group.groundInSpace().units().entrySet()) {
            int carried = Math.min(room, ground.getValue());
            lost += (ground.getValue() - carried) * IncomingForces.cost(seat, ground.getKey());
            room -= carried;
        }
        lost += Math.max(0, fighters - room) * IncomingForces.cost(seat, UnitType.Fighter);
        return lost;
    }

    private int sustainable(Defenders group) {
        int sustain = 0;
        for (Map.Entry<UnitType, Integer> ship : group.ships().units().entrySet()) {
            boolean sustains = BoardView.model(seat, ship.getKey())
                    .map(UnitModel::getSustainDamage)
                    .orElse(false);
            if (sustains) sustain += ship.getValue() - group.ships().damaged().getOrDefault(ship.getKey(), 0);
        }
        return sustain;
    }

    private List<Unit> unitsCheapestFirst(Defenders group) {
        List<Unit> units = new ArrayList<>();
        group.ships().units().forEach((type, count) -> {
            int capacity = type == UnitType.Fighter ? 0 : BoardView.capacity(seat, type);
            for (int i = 0; i < count; i++) units.add(new Unit(type, IncomingForces.cost(seat, type), capacity));
        });
        units.sort(Comparator.comparingDouble(Unit::cost).thenComparingInt(Unit::capacity));
        return units;
    }
}
