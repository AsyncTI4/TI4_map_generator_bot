package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.scoring.Wallet.PlanetValue;

class WalletTest {

    private static Wallet wallet(int tradeGoods, PlanetValue... planets) {
        return new Wallet(List.of(planets), tradeGoods, 3, 2);
    }

    @Test
    void paysResourcesWithPlanetsBeforeTradeGoods() {
        Wallet wallet = wallet(5, new PlanetValue("a", 4, 1), new PlanetValue("b", 3, 2), new PlanetValue("c", 1, 3));

        Wallet.Payment payment = wallet.plan(SpendCost.resources(8)).orElseThrow();

        assertThat(payment.forResources()).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(payment.tradeGoods()).isZero();
    }

    @Test
    void coversAShortfallWithTradeGoods() {
        Wallet wallet = wallet(2, new PlanetValue("a", 4, 1), new PlanetValue("b", 3, 2));

        Wallet.Payment payment = wallet.plan(SpendCost.resources(8)).orElseThrow();

        assertThat(payment.tradeGoods()).isEqualTo(1);
        assertThat(wallet.canPay(SpendCost.resources(10))).isFalse();
    }

    // Amass Wealth: each planet pays either resources or influence, never both, and 3 trade goods are always due.
    @Test
    void splitsPlanetsBetweenResourcesAndInfluence() {
        Wallet wallet = wallet(3, new PlanetValue("mining", 3, 0), new PlanetValue("council", 0, 3));

        Wallet.Payment payment = wallet.plan(SpendCost.each(3)).orElseThrow();

        assertThat(payment.forResources()).containsExactly("mining");
        assertThat(payment.forInfluence()).containsExactly("council");
        assertThat(payment.tradeGoods()).isEqualTo(3);
    }

    @Test
    void refusesWhenOnePlanetWouldHaveToPayTwice() {
        Wallet wallet = wallet(3, new PlanetValue("both", 3, 3));

        assertThat(wallet.canPay(SpendCost.each(3))).isFalse();
    }

    @Test
    void prefersTheLeastValuablePlanetsThatCoverTheCost() {
        Wallet wallet =
                wallet(0, new PlanetValue("big", 5, 5), new PlanetValue("small", 2, 0), new PlanetValue("mid", 3, 0));

        Wallet.Payment payment = wallet.plan(SpendCost.resources(5)).orElseThrow();

        assertThat(payment.forResources()).containsExactlyInAnyOrder("small", "mid");
    }

    @Test
    void checksTokensAndTradeGoodsDirectly() {
        Wallet wallet = wallet(4, new PlanetValue("a", 1, 1));

        assertThat(wallet.canPay(SpendCost.tokens(5))).isTrue();
        assertThat(wallet.canPay(SpendCost.tokens(6))).isFalse();
        assertThat(wallet.canPay(SpendCost.tradeGoods(5))).isFalse();
    }
}
