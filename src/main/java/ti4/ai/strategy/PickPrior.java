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
    private static final Map<StrategyCard, int[]> HACAN_PICKS_BY_ROUND = new EnumMap<>(StrategyCard.class);
    private static final Map<StrategyCard, int[]> XXCHA_PICKS_BY_ROUND = new EnumMap<>(StrategyCard.class);
    private static final Map<StrategyCard, int[]> SARDAKK_PICKS_BY_ROUND = new EnumMap<>(StrategyCard.class);
    private static final Map<StrategyCard, int[]> LETNEV_PICKS_BY_ROUND = new EnumMap<>(StrategyCard.class);
    private static final Map<StrategyCard, int[]> SOL_PICKS_BY_ROUND = new EnumMap<>(StrategyCard.class);
    private static final Map<String, Map<StrategyCard, int[]>> PICKS_BY_FACTION = Map.of(
            "hacan", HACAN_PICKS_BY_ROUND,
            "xxcha", XXCHA_PICKS_BY_ROUND,
            "sardakk", SARDAKK_PICKS_BY_ROUND,
            "letnev", LETNEV_PICKS_BY_ROUND,
            "sol", SOL_PICKS_BY_ROUND);

    static {
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.LEADERSHIP, new int[] {121, 81, 108, 108, 119, 20});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.DIPLOMACY, new int[] {69, 50, 49, 45, 93, 36});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.POLITICS, new int[] {114, 165, 163, 153, 116, 26});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.CONSTRUCTION, new int[] {140, 129, 115, 89, 76, 23});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.TRADE, new int[] {144, 167, 118, 111, 57, 13});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.WARFARE, new int[] {216, 154, 103, 125, 134, 36});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.TECHNOLOGY, new int[] {70, 87, 81, 73, 52, 12});
        NEKRO_PICKS_BY_ROUND.put(StrategyCard.IMPERIAL, new int[] {6, 43, 137, 168, 141, 39});

        HACAN_PICKS_BY_ROUND.put(StrategyCard.LEADERSHIP, new int[] {142, 165, 114, 91, 99, 22});
        HACAN_PICKS_BY_ROUND.put(StrategyCard.DIPLOMACY, new int[] {60, 45, 28, 49, 66, 13});
        HACAN_PICKS_BY_ROUND.put(StrategyCard.POLITICS, new int[] {159, 128, 97, 68, 58, 22});
        HACAN_PICKS_BY_ROUND.put(StrategyCard.CONSTRUCTION, new int[] {112, 164, 130, 118, 92, 29});
        HACAN_PICKS_BY_ROUND.put(StrategyCard.TRADE, new int[] {220, 185, 167, 138, 143, 36});
        HACAN_PICKS_BY_ROUND.put(StrategyCard.WARFARE, new int[] {89, 38, 71, 124, 104, 22});
        HACAN_PICKS_BY_ROUND.put(StrategyCard.TECHNOLOGY, new int[] {208, 189, 258, 228, 147, 28});
        HACAN_PICKS_BY_ROUND.put(StrategyCard.IMPERIAL, new int[] {3, 75, 121, 167, 162, 30});

        XXCHA_PICKS_BY_ROUND.put(StrategyCard.LEADERSHIP, new int[] {111, 91, 102, 112, 93, 30});
        XXCHA_PICKS_BY_ROUND.put(StrategyCard.DIPLOMACY, new int[] {238, 170, 91, 99, 93, 46});
        XXCHA_PICKS_BY_ROUND.put(StrategyCard.POLITICS, new int[] {80, 116, 100, 107, 124, 23});
        XXCHA_PICKS_BY_ROUND.put(StrategyCard.CONSTRUCTION, new int[] {72, 118, 101, 61, 52, 14});
        XXCHA_PICKS_BY_ROUND.put(StrategyCard.TRADE, new int[] {109, 86, 80, 70, 29, 10});
        XXCHA_PICKS_BY_ROUND.put(StrategyCard.WARFARE, new int[] {90, 31, 36, 40, 83, 33});
        XXCHA_PICKS_BY_ROUND.put(StrategyCard.TECHNOLOGY, new int[] {90, 135, 162, 169, 116, 29});
        XXCHA_PICKS_BY_ROUND.put(StrategyCard.IMPERIAL, new int[] {1, 40, 113, 127, 118, 30});

        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.LEADERSHIP, new int[] {160, 169, 196, 188, 147, 44});
        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.DIPLOMACY, new int[] {105, 90, 51, 43, 99, 31});
        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.POLITICS, new int[] {172, 149, 135, 146, 136, 31});
        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.CONSTRUCTION, new int[] {40, 119, 114, 104, 104, 28});
        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.TRADE, new int[] {129, 138, 134, 113, 61, 12});
        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.WARFARE, new int[] {267, 96, 101, 128, 136, 40});
        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.TECHNOLOGY, new int[] {117, 175, 134, 96, 72, 17});
        SARDAKK_PICKS_BY_ROUND.put(StrategyCard.IMPERIAL, new int[] {1, 53, 122, 163, 135, 35});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.LEADERSHIP, new int[] {189, 237, 205, 220, 185, 40});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.DIPLOMACY, new int[] {175, 93, 53, 87, 118, 37});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.POLITICS, new int[] {228, 143, 135, 164, 141, 31});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.CONSTRUCTION, new int[] {51, 136, 141, 115, 92, 35});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.TRADE, new int[] {169, 132, 125, 100, 44, 16});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.WARFARE, new int[] {97, 88, 109, 116, 148, 37});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.TECHNOLOGY, new int[] {188, 175, 152, 148, 99, 26});
        LETNEV_PICKS_BY_ROUND.put(StrategyCard.IMPERIAL, new int[] {2, 93, 164, 141, 148, 46});
        SOL_PICKS_BY_ROUND.put(StrategyCard.LEADERSHIP, new int[] {168, 247, 168, 161, 163, 38});
        SOL_PICKS_BY_ROUND.put(StrategyCard.DIPLOMACY, new int[] {149, 110, 89, 95, 139, 47});
        SOL_PICKS_BY_ROUND.put(StrategyCard.POLITICS, new int[] {231, 188, 170, 182, 175, 39});
        SOL_PICKS_BY_ROUND.put(StrategyCard.CONSTRUCTION, new int[] {69, 138, 119, 121, 81, 18});
        SOL_PICKS_BY_ROUND.put(StrategyCard.TRADE, new int[] {204, 157, 195, 174, 92, 23});
        SOL_PICKS_BY_ROUND.put(StrategyCard.WARFARE, new int[] {153, 54, 66, 108, 134, 42});
        SOL_PICKS_BY_ROUND.put(StrategyCard.TECHNOLOGY, new int[] {217, 192, 150, 136, 111, 26});
        SOL_PICKS_BY_ROUND.put(StrategyCard.IMPERIAL, new int[] {3, 105, 224, 207, 179, 53});
    }

    static double nudge(StrategyCard card, int round) {
        return nudge(NEKRO_PICKS_BY_ROUND, card, round);
    }

    static double nudge(String faction, StrategyCard card, int round) {
        return nudge(PICKS_BY_FACTION.getOrDefault(faction, NEKRO_PICKS_BY_ROUND), card, round);
    }

    private static double nudge(Map<StrategyCard, int[]> picksByRound, StrategyCard card, int round) {
        int[] picks = picksByRound.get(card);
        if (picks == null) return 0;
        int index = Math.clamp(round, FIRST_ROUND, LAST_RECORDED_ROUND) - FIRST_ROUND;
        int roundTotal =
                picksByRound.values().stream().mapToInt(counts -> counts[index]).sum();
        double share = (double) picks[index] / roundTotal;
        return CardValueScale.PRIOR_WEIGHT * Math.clamp(EVEN_SHARE_SCALE * share - 1, -1, 1);
    }
}
