package ti4.ai.tactical;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

record Strike(
        Tile target,
        Player opponent,
        List<Tile> origins,
        List<UnitMove> moves,
        Map<UnitType, Integer> ships,
        Map<UnitType, Integer> damaged,
        Map<UnitType, Integer> cargo,
        Map<UnitType, Integer> extraDefenderShips,
        Map<UnitType, Integer> extraDefenderGround,
        StrikeRules rules,
        Map<UnitType, Integer> pulled) {

    Strike withMoves(List<UnitMove> executed) {
        return new Strike(
                target,
                opponent,
                origins,
                executed,
                ships,
                damaged,
                cargo,
                extraDefenderShips,
                extraDefenderGround,
                rules,
                pulled);
    }

    Strike withExtraDefenderShips(Map<UnitType, Integer> defenders) {
        return new Strike(
                target, opponent, origins, moves, ships, damaged, cargo, defenders, extraDefenderGround, rules, pulled);
    }

    Strike withPulled(Map<UnitType, Integer> commanderForces) {
        return new Strike(
                target,
                opponent,
                origins,
                moves,
                ships,
                damaged,
                cargo,
                extraDefenderShips,
                extraDefenderGround,
                rules,
                commanderForces);
    }

    static Strike of(
            Player seat, Tile target, Player opponent, List<Tile> origins, List<UnitMove> moves, StrikeRules rules) {
        Map<UnitType, Integer> ships = new EnumMap<>(UnitType.class);
        Map<UnitType, Integer> damaged = new EnumMap<>(UnitType.class);
        Map<UnitType, Integer> cargo = new EnumMap<>(UnitType.class);
        for (UnitMove move : moves) {
            if (BoardView.GROUND_FORCES.contains(move.type())) {
                cargo.merge(move.type(), move.count(), Integer::sum);
                continue;
            }
            ships.merge(move.type(), move.count(), Integer::sum);
            Tile origin = origins.stream()
                    .filter(tile -> tile.getPosition().equals(move.origin()))
                    .findFirst()
                    .orElse(null);
            if (origin == null) continue;
            int hurt = Math.min(move.count(), BoardView.damaged(BoardView.space(origin), seat, move.type()));
            if (hurt > 0) damaged.merge(move.type(), hurt, Integer::sum);
        }
        return new Strike(target, opponent, origins, moves, ships, damaged, cargo, Map.of(), Map.of(), rules, Map.of());
    }

    int pulledForces() {
        return pulled.values().stream().mapToInt(Integer::intValue).sum();
    }
}
