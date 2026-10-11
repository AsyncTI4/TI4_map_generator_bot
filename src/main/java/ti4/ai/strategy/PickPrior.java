package ti4.ai.strategy;

import java.util.EnumMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;

@UtilityClass
class PickPrior {

    private static final int FIRST_ROUND = 1;
    private static final int LAST_RECORDED_ROUND = 6;
    private static final double EVEN_SHARE_SCALE = 8;
    private static final Map<StrategyCard, int[]> NEKRO_PICKS_BY_ROUND = new EnumMap<>(StrategyCard.class);

    static {
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.LEADERSHIP, new int[] {121, 81, 108, 108, 119, 20});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.DIPLOMACY, new int[] {69, 50, 49, 45, 93, 36});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.POLITICS, new int[] {114, 165, 163, 153, 116, 26});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.CONSTRUCTION, new int[] {140, 129, 115, 89, 76, 23});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.TRADE, new int[] {144, 167, 118, 111, 57, 13});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.WARFARE, new int[] {216, 154, 103, 125, 134, 36});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.TECHNOLOGY, new int[] {70, 87, 81, 73, 52, 12});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.IMPERIAL, new int[] {6, 43, 137, 168, 141, 39});
    }

    static double nudge(StrategyCard card, int round) {
        int[] picks = NEKRO_PICKS_BY_ROUND.get(card);
        if (picks == null) return 0;
        int index = Math.clamp(round, FIRST_ROUND, LAST_RECORDED_ROUND) - FIRST_ROUND;
        int roundTotal = NEKRO_PICKS_BY_ROUND.values().stream()
                .mapToInt(counts -> counts[index])
                .sum();
        double share = (double) picks[index] / roundTotal;
        return CardValueScale.PRIOR_WEIGHT * Math.clamp(EVEN_SHARE_SCALE * share - 1, -1, 1);
    }
}
