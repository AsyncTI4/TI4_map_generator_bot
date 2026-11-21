package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// The trade goods expedition costs 3 trade goods. A seat that holds exactly what Amass Wealth asks for (3 resources,
// 3 influence and 3 trade goods) at the start of the round keeps them for the status phase and does not claim it.
class ExpeditionReserveTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.aiIsActive("action");
        test.nekroHome();
        test.place("34", "201");
        test.nekro.addPlanet("centauri");
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
    }

    private boolean refusesTheTradeGoodsExpedition() {
        return KindPricing.of(
                        test.game,
                        test.nekro,
                        ExpeditionKind.TRADE_GOODS,
                        Holdings.of(test.game, test.nekro),
                        SpendHorizon.AHEAD)
                .isEmpty();
    }

    @Test
    void refusesToSpendTradeGoodsTheObjectiveNeeds() {
        test.nekro.setTg(3);

        assertThat(refusesTheTradeGoodsExpedition()).isTrue();
    }

    @Test
    void claimsItWithTradeGoodsToSpare() {
        test.nekro.setTg(6);

        assertThat(refusesTheTradeGoodsExpedition()).isFalse();
    }

    @Test
    void claimsItWhenNoObjectiveIsWaiting() {
        test.game.getRevealedPublicObjectives().remove("amass_wealth");
        test.nekro.setTg(3);

        assertThat(refusesTheTradeGoodsExpedition()).isFalse();
    }
}
