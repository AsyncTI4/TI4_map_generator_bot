package ti4.ai.scoring;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ObjectiveCatalog {

    private static final Map<String, SpendCost> SPEND_COSTS = Map.of(
            "monument", SpendCost.resources(8),
            "sway_council", SpendCost.influence(8),
            "trade_routes", SpendCost.tradeGoods(5),
            "amass_wealth", SpendCost.each(3),
            "lead", SpendCost.tokens(3),
            "golden_age", SpendCost.resources(16),
            "manipulate_law", SpendCost.influence(16),
            "centralize_trade", SpendCost.tradeGoods(10),
            "galvanize", SpendCost.tokens(6),
            "vast_reserves", SpendCost.each(6));

    public static Optional<SpendCost> spendCost(String objectiveId) {
        return Optional.ofNullable(SPEND_COSTS.get(objectiveId));
    }

    public static boolean isSpend(String objectiveId) {
        return SPEND_COSTS.containsKey(objectiveId);
    }
}
