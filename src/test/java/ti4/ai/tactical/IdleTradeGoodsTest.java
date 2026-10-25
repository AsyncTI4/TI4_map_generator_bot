package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class IdleTradeGoodsTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
    }

    // Humans end a round with about three trade goods; anything above that, and above what is reserved, is idle.
    @Test
    void countsTheTradeGoodsAboveWhatIsKept() {
        test.nekro.setTg(12);

        assertThat(IdleTradeGoods.surplus(test.game, test.nekro))
                .isEqualTo(12 - IdleTradeGoods.KEPT_TRADE_GOODS)
                .isPositive();
    }

    @Test
    void aSeatHoldingAFewTradeGoodsHasNoSurplus() {
        test.nekro.setTg(IdleTradeGoods.KEPT_TRADE_GOODS);

        assertThat(IdleTradeGoods.surplus(test.game, test.nekro)).isZero();
    }
}
