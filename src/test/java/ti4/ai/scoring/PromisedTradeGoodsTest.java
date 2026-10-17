package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class PromisedTradeGoodsTest extends BaseTi4Test {

    private AiTestGame test;

    // Nekro has built offers giving Sol 5 trade goods (in two presses) and 2 commodities, and asked for 4 trade goods
    // back; it also offers Letnev 1 trade good.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.addSeat("200000000000000002", "letnev", "red");
        test.aiIsActive("action");
        test.nekro.addTransactionItem("sendingnekro_receivingsol_TGs_3");
        test.nekro.addTransactionItem("sendingnekro_receivingsol_TGs_2");
        test.nekro.addTransactionItem("sendingnekro_receivingsol_Comms_2");
        test.nekro.addTransactionItem("sendingsol_receivingnekro_TGs_4");
        test.nekro.addTransactionItem("sendingnekro_receivingletnev_TGs_1");
    }

    @Test
    void sumsTheTradeGoodsItHasOffered() {
        assertThat(PromisedTradeGoods.of(test.nekro)).isEqualTo(6);
        assertThat(PromisedTradeGoods.to(test.nekro, test.sol)).isEqualTo(5);
        assertThat(PromisedTradeGoods.sent(test.nekro, "Comms", null)).isEqualTo(2);
        assertThat(PromisedTradeGoods.of(test.sol)).isZero();
    }

    // Production, research and follows keep back trade goods already offered, but only in the action phase.
    @Test
    void theReserveKeepsOfferedTradeGoodsInTheActionPhase() {
        assertThat(ScoringReserve.of(test.game, test.nekro)).isEqualTo(SpendCost.tradeGoods(6));

        test.game.setPhaseOfGame("agenda");

        assertThat(ScoringReserve.of(test.game, test.nekro)).isEqualTo(SpendCost.NONE);
    }

    // With nothing offered the reserve is what it always was.
    @Test
    void nothingOfferedChangesNothing() {
        test.nekro.getTransactionItems().clear();

        assertThat(ScoringReserve.of(test.game, test.nekro)).isEqualTo(SpendCost.NONE);
    }
}
