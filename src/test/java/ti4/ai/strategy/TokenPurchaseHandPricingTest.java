package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.ai.HandPricingRecursionTable;
import ti4.testUtils.BaseTi4Test;

// Buying Leadership tokens weighs the open expedition, whose action card slice prices a hand holding Strategize, whose
// follow asks again what Leadership would buy. That loop used to overflow the stack.
class TokenPurchaseHandPricingTest extends BaseTi4Test {

    private static final Duration LIMIT = Duration.ofSeconds(60);

    private static Optional<TokenPurchase.Purchase> leadershipPurchase(HandPricingRecursionTable table) {
        int reinforcements = StrategyCardRules.reinforcements(table.game, table.seat);
        return assertTimeoutPreemptively(LIMIT, () -> TokenPurchase.best(table.game, table.seat, reinforcements));
    }

    @Test
    void theLeadershipPurchaseIsPricedWhenStrategizeWouldWeighTechnology() {
        assertThat(leadershipPurchase(HandPricingRecursionTable.throughTechnology())
                        .map(TokenPurchase.Purchase::tokens)
                        .orElse(0))
                .isBetween(0, TokenPurchase.MAX_TOKENS + 1);
    }

    @Test
    void theLeadershipPurchaseIsPricedWhenStrategizeWouldFollowDiplomacy() {
        assertThat(leadershipPurchase(HandPricingRecursionTable.throughDiplomacy())
                        .map(TokenPurchase.Purchase::tokens)
                        .orElse(0))
                .isBetween(0, TokenPurchase.MAX_TOKENS + 1);
    }

    @Test
    void idleTradeGoodsAreCountedWhenStrategizeWouldWeighTechnology() {
        HandPricingRecursionTable table = HandPricingRecursionTable.throughTechnology();
        assertThat(assertTimeoutPreemptively(
                        LIMIT, () -> ti4.ai.tactical.IdleTradeGoods.surplus(table.game, table.seat)))
                .isNotNegative();
    }
}
