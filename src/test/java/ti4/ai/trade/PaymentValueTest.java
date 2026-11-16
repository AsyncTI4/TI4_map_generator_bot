package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.promise.PromisePayment;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// User (2026-11-14): when a payer is short of trade goods it may pay in debt or promissory notes instead.
class PaymentValueTest extends BaseTi4Test {

    private AiTestGame test;
    private Player payer;
    private Player receiver;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        payer = test.sol;
        receiver = test.nekro;
        test.game.setPhaseOfGame("agendaVoting");
        payer.setTg(0);
    }

    // Trade goods alone cover the need, so there is no other payment to make.
    @Test
    void tradeGoodsFirst() {
        payer.setTg(5);

        assertThat(PaymentValue.cheapestCovering(test.game, payer, receiver, 2.0, 5))
                .isEmpty();
    }

    // With no trade goods, debt fills the gap: each token is worth less than a trade good to the receiver, so it
    // takes more tokens than trade goods.
    @Test
    void debtFillsTheGap() {
        PromisePayment payment = PaymentValue.cheapestCovering(test.game, payer, receiver, 2.0, 0)
                .orElseThrow();

        assertThat(payment.tradeGoods()).isZero();
        assertThat(payment.debt()).isGreaterThanOrEqualTo(3);
        assertThat(PaymentValue.receivedAsPayerSees(test.game, payer, receiver, payment))
                .isGreaterThanOrEqualTo(2.0);
    }

    // The payer spends what trade goods it has and pays the rest in debt.
    @Test
    void someTradeGoodsAndTheRestInDebt() {
        PromisePayment payment = PaymentValue.cheapestCovering(test.game, payer, receiver, 2.5, 1)
                .orElseThrow();

        assertThat(payment.tradeGoods()).isEqualTo(1);
        assertThat(payment.debt()).isPositive();
    }

    // With no debt room left, a promissory note the payer holds pays instead, if it covers the need.
    @Test
    void aNoteWhenThereIsNoDebtRoom() {
        receiver.addDebtTokens(payer.getColor(), DebtRules.DEBT_CAP);
        payer.setPromissoryNote(payer.getColor() + "_cf");

        assertThat(PaymentValue.cheapestCovering(test.game, payer, receiver, 0.3, 0))
                .map(PromisePayment::notes)
                .contains(List.of(payer.getColor() + "_cf"));
        assertThat(PaymentValue.cheapestCovering(test.game, payer, receiver, 50.0, 0))
                .isEmpty();
    }
}
