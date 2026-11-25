package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.helpers.Units.UnitType;

@UtilityClass
class FleetSupplyFit {

    static List<UnitMove> fit(
            List<UnitMove> ships,
            int room,
            boolean keepHauler,
            ToDoubleFunction<UnitType> cost,
            ToDoubleFunction<UnitType> capacity,
            int perFleetSlot) {
        List<UnitMove> fighters =
                ships.stream().filter(move -> move.type() == UnitType.Fighter).toList();
        if (nonFighters(ships) <= room) {
            List<UnitMove> warships = ships.stream()
                    .filter(move -> move.type() != UnitType.Fighter)
                    .toList();
            return withFighters(warships, fighters, room - nonFighters(warships), capacity, perFleetSlot);
        }
        List<UnitMove> pool = new ArrayList<>(
                ships.stream().filter(move -> move.type() != UnitType.Fighter).toList());
        List<UnitMove> kept = new ArrayList<>();
        int left = room;
        if (keepHauler && left > 0) {
            UnitMove hauler = pool.stream()
                    .filter(move -> capacity.applyAsDouble(move.type()) > 0)
                    .max(Comparator.comparingDouble(move -> capacity.applyAsDouble(move.type())))
                    .orElse(null);
            if (hauler != null) {
                kept.add(withCount(hauler, 1));
                pool.set(pool.indexOf(hauler), withCount(hauler, hauler.count() - 1));
                left--;
            }
        }
        pool.sort(Comparator.comparingDouble((UnitMove move) -> cost.applyAsDouble(move.type()))
                .reversed());
        for (UnitMove move : pool) {
            int count = Math.min(left, move.count());
            if (count <= 0) continue;
            kept.add(withCount(move, count));
            left -= count;
        }
        List<UnitMove> merged = merged(kept);
        if (merged.isEmpty()) return List.of();
        return withFighters(merged, fighters, left, capacity, perFleetSlot);
    }

    private static List<UnitMove> withFighters(
            List<UnitMove> warships,
            List<UnitMove> fighters,
            int leftRoom,
            ToDoubleFunction<UnitType> capacity,
            int perFleetSlot) {
        double carried = warships.stream()
                .mapToDouble(move -> move.count() * capacity.applyAsDouble(move.type()))
                .sum();
        int allowed = (int) carried + Math.max(0, leftRoom) * perFleetSlot;
        List<UnitMove> result = new ArrayList<>(warships);
        for (UnitMove fighter : fighters) {
            int count = Math.min(allowed, fighter.count());
            if (count <= 0) continue;
            result.add(withCount(fighter, count));
            allowed -= count;
        }
        return result;
    }

    private static int nonFighters(List<UnitMove> ships) {
        return ships.stream()
                .filter(move -> move.type() != UnitType.Fighter)
                .mapToInt(UnitMove::count)
                .sum();
    }

    private static UnitMove withCount(UnitMove move, int count) {
        return new UnitMove(move.origin(), move.holder(), move.type(), count);
    }

    private static List<UnitMove> merged(List<UnitMove> moves) {
        List<UnitMove> merged = new ArrayList<>();
        for (UnitMove move : moves) {
            if (move.count() <= 0) continue;
            int at = indexOfSame(merged, move);
            if (at < 0) merged.add(move);
            else merged.set(at, withCount(merged.get(at), merged.get(at).count() + move.count()));
        }
        return merged;
    }

    private static int indexOfSame(List<UnitMove> moves, UnitMove move) {
        for (int index = 0; index < moves.size(); index++) {
            UnitMove other = moves.get(index);
            if (other.type() == move.type()
                    && other.origin().equals(move.origin())
                    && other.holder().equals(move.holder())) return index;
        }
        return -1;
    }
}
