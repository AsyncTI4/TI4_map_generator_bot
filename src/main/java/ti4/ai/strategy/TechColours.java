package ti4.ai.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;

@UtilityClass
class TechColours {

    private static final Map<Character, TechnologyType> LETTERS = Map.of(
            'B', TechnologyType.PROPULSION,
            'G', TechnologyType.BIOTIC,
            'R', TechnologyType.WARFARE,
            'Y', TechnologyType.CYBERNETIC);
    private static final int MIN_POOL = 2;

    static List<TechnologyType> pool(Player seat) {
        List<TechnologyType> pool = TechnologyType.mainFour.stream()
                .filter(seat.getSynergies()::contains)
                .toList();
        return pool.size() < MIN_POOL ? List.of() : pool;
    }

    static boolean pooled(Player seat, TechnologyType colour) {
        return pool(seat).contains(colour);
    }

    static Map<TechnologyType, Integer> owned(Player seat, @Nullable TechnologyModel adding) {
        Map<TechnologyType, Integer> owned = new EnumMap<>(TechnologyType.class);
        for (TechnologyType colour : TechnologyType.mainFour) {
            int count = ButtonHelper.getNumberOfCertainTypeOfTech(seat, colour);
            owned.put(colour, adding != null && adding.getTypes().contains(colour) ? count + 1 : count);
        }
        return owned;
    }

    static Map<TechnologyType, Integer> missing(
            Player seat, String requirements, Map<TechnologyType, Integer> owned, Map<TechnologyType, Integer> skips) {
        List<TechnologyType> pool = pool(seat);
        Map<TechnologyType, Integer> missing = new EnumMap<>(TechnologyType.class);
        int pooledNeeded = 0;
        int pooledHave = 0;
        for (TechnologyType colour : TechnologyType.mainFour) {
            int needed = needed(requirements, colour);
            int have = owned.getOrDefault(colour, 0) + skips.getOrDefault(colour, 0);
            if (pool.contains(colour)) {
                pooledNeeded += needed;
                pooledHave += have;
            } else if (needed > have) {
                missing.put(colour, needed - have);
            }
        }
        if (pooledNeeded > pooledHave) missing.put(pool.getFirst(), pooledNeeded - pooledHave);
        return missing;
    }

    static int lawsProgress(Player seat, Map<TechnologyType, Integer> owned) {
        List<TechnologyType> pool = pool(seat);
        int best = 0;
        int pooledTotal = 0;
        for (TechnologyType colour : TechnologyType.mainFour) {
            int count = owned.getOrDefault(colour, 0);
            if (pool.contains(colour)) {
                pooledTotal += count;
            } else {
                best = Math.max(best, count);
            }
        }
        return Math.max(best, pooledTotal);
    }

    private static int needed(String requirements, TechnologyType colour) {
        return (int) requirements
                .chars()
                .filter(letter -> LETTERS.get((char) letter) == colour)
                .count();
    }
}
