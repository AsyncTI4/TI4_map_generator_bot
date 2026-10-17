package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class ExtraDieTarget {

    static final int MECH_NEAR_TIE = 1;

    public static OptionalInt choose(List<Combatant> units, boolean ground) {
        Comparator<Integer> bestChanceToHit = Comparator.comparingInt(
                        (Integer index) -> units.get(index).hitsOn())
                .thenComparing(index -> units.get(index).dice(), Comparator.reverseOrder())
                .thenComparingDouble(index -> units.get(index).cost());
        List<Integer> rolling = IntStream.range(0, units.size())
                .filter(index ->
                        units.get(index).type() != null && units.get(index).dice() > 0)
                .boxed()
                .toList();
        OptionalInt best = rolling.stream().min(bestChanceToHit).stream()
                .mapToInt(Integer::intValue)
                .findFirst();
        if (best.isEmpty() || !ground) return best;
        int bestHitsOn = units.get(best.getAsInt()).hitsOn();
        OptionalInt mech = rolling.stream()
                .filter(index -> units.get(index).type() == UnitType.Mech)
                .filter(index -> units.get(index).hitsOn() <= bestHitsOn + MECH_NEAR_TIE)
                .min(bestChanceToHit)
                .stream()
                .mapToInt(Integer::intValue)
                .findFirst();
        return mech.isPresent() ? mech : best;
    }
}
