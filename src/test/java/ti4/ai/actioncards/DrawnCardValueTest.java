package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// Nekro Malleon, the Nekro agent, lets its holder discard an action card for 2 trade goods. While it is ready and no
// card in hand is already spare for it, a drawn card is worth at least those 2 trade goods.
class DrawnCardValueTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
    }

    @Test
    void anAverageCardWithoutTheAgent() {
        assertThat(DrawnCardValue.of(test.game, test.sol, 1)).isEqualTo(ActionCardPrior.AVERAGE_CARD);
    }

    @Test
    void twoTradeGoodsWhileTheAgentIsReady() {
        assertThat(test.nekro.hasUnexhaustedLeader("nekroagent")).isTrue();

        assertThat(DrawnCardValue.of(test.game, test.nekro, 1)).isEqualTo(2.0);
    }

    @Test
    void anAverageCardOnceTheAgentIsExhausted() {
        assertThat(test.nekro.hasUnexhaustedLeader("nekroagent")).isTrue();
        test.nekro.getLeader("nekroagent").orElseThrow().setExhausted(true);

        assertThat(DrawnCardValue.of(test.game, test.nekro, 1)).isEqualTo(ActionCardPrior.AVERAGE_CARD);
    }

    // Once the first drawn card is in hand it is the spare one, so the agent lifts only the first card of a draw.
    @Test
    void onlyTheFirstCardOfADrawIsWorthTheTradeGoods() {
        assertThat(DrawnCardValue.of(test.game, test.nekro, 2)).isEqualTo(2.0 + ActionCardPrior.AVERAGE_CARD);
    }

    // A card in hand is already spare for the agent, so a drawn card adds no trade goods through it.
    @Test
    void anAverageCardWhenTheHandAlreadyHasASpareCard() {
        assertThat(test.nekro.hasUnexhaustedLeader("nekroagent")).isTrue();
        for (int card = 1; card <= 7; card++) test.nekro.setActionCard("sabo" + card, card);
        assertThat(ActionCardValue.hasSpareCard(test.game, test.nekro)).isTrue();

        assertThat(DrawnCardValue.of(test.game, test.nekro, 1)).isEqualTo(ActionCardPrior.AVERAGE_CARD);
    }
}
