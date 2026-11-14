package ti4.ai.promise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class PromisePaymentTest extends BaseTi4Test {

    // A promise paid only in trade goods stores nothing extra: its price is the whole payment.
    @Test
    void aTradeGoodsOnlyPromiseReadsItsPrice() {
        AiTestGame test = new AiTestGame();
        Promise promise = offer(test, 3);
        PromisePayment.attach(test.game, promise.id(), PromisePayment.tradeGoods(3));

        assertThat(PromisePayment.of(test.game, promise)).isEqualTo(PromisePayment.tradeGoods(3));
        assertThat(PromisePayment.of(test.game, promise).describe()).isEqualTo("3 trade goods");
        assertThat(PromisePayment.of(test.game, promise).label()).isEqualTo("3 TG");
    }

    // Debt and notes are kept beside the promise and survive the stored-value round trip.
    @Test
    void debtAndNotesAreStoredBesideThePromise() {
        AiTestGame test = new AiTestGame();
        Promise promise = offer(test, 1);
        PromisePayment payment = new PromisePayment(1, 2, List.of("blue_cf"));
        PromisePayment.attach(test.game, promise.id(), payment);

        assertThat(PromisePayment.of(test.game, promise)).isEqualTo(payment);
        assertThat(payment.describe()).startsWith("1 trade good, 2 debt tokens and ");
        assertThat(PromisePayment.decode(payment.encode())).contains(payment);
    }

    // A payer can only pay notes it still holds outside its play area.
    @Test
    void aPaymentNeedsTheNotesInHand() {
        AiTestGame test = new AiTestGame();
        PromisePayment payment = new PromisePayment(0, 1, List.of("blue_cf"));
        test.sol.setTg(0);

        assertThat(payment.affordable(test.sol))
                .isEqualTo(test.sol.getPromissoryNotes().containsKey("blue_cf"));
        assertThat(PromisePayment.tradeGoods(1).affordable(test.sol)).isFalse();
    }

    private static Promise offer(AiTestGame test, int price) {
        return Promise.offer(
                test.game,
                Promise.Term.NOT_AGAINST,
                test.sol,
                test.nekro,
                test.nekro,
                "agenda",
                "for",
                0,
                price,
                AiTestGame.NOW,
                AiTestGame.NOW + 60_000);
    }
}
