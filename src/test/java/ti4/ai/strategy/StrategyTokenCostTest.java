package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class StrategyTokenCostTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
    }

    private double cost(int strategyTokens) {
        test.nekro.setStrategicCC(strategyTokens);
        return StrategyTokenCost.toSpend(test.game, test.nekro).orElseThrow();
    }

    // A token is worth a resource on the card scale; the last one costs more because the seat then cannot follow
    // a strategy card, and a surplus one costs less.
    @Test
    void aTokenCostsMoreTheScarcerTheStrategyPoolIs() {
        assertThat(cost(1)).isCloseTo(1.5 * CardFacade.strategyToken(), within(EXACT));
        assertThat(cost(2)).isCloseTo(CardFacade.strategyToken(), within(EXACT));
        assertThat(cost(3)).isCloseTo(0.75 * CardFacade.strategyToken(), within(EXACT));
        assertThat(cost(5)).isCloseTo(0.75 * CardFacade.strategyToken(), within(EXACT));
    }

    @Test
    void anEmptyStrategyPoolHasNothingToSpend() {
        test.nekro.setStrategicCC(0);

        assertThat(StrategyTokenCost.toSpend(test.game, test.nekro)).isEmpty();
        assertThat(StrategyTokenCost.canSpare(test.game, test.nekro)).isFalse();
    }
}
