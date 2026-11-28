package ti4.ai.tactical;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
class CapacityUse {

    static final double EMPTY_SLOT_VALUE = 0.5;
    private static final int HAULER_CAPACITY = 3;

    static boolean hauler(Player seat, UnitType type) {
        return BoardView.model(seat, type)
                .filter(model -> model.getCapacityValue() >= HAULER_CAPACITY && !model.getSustainDamage())
                .isPresent();
    }

    static int cargo(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.GROUND_FORCES.contains(move.type()) || move.type() == UnitType.Fighter)
                .mapToInt(UnitMove::count)
                .sum();
    }

    static double emptyHaulerPenalty(Player seat, List<UnitMove> moves) {
        int cargoLeft = cargo(moves);
        double penalty = 0;
        for (UnitMove move : moves) {
            if (!BoardView.MOVING_SHIPS.contains(move.type()) || !hauler(seat, move.type())) continue;
            int capacity = BoardView.capacity(seat, move.type());
            for (int ship = 0; ship < move.count(); ship++) {
                int carried = Math.min(capacity, cargoLeft);
                cargoLeft -= carried;
                if (carried == 0) penalty += EMPTY_SLOT_VALUE * capacity;
            }
        }
        return penalty;
    }

    static double loneHaulerPenalty(Player seat, List<UnitMove> moves) {
        boolean onlyHaulers = moves.stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .allMatch(move -> hauler(seat, move.type()));
        return onlyHaulers ? emptyHaulerPenalty(seat, moves) : 0;
    }
}
