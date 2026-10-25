package ti4.ai.hacan;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// Quantum Datahub Node is offered at the end of the strategy phase. The AI seat is given the technology and holds
// Diplomacy (2), which it has little use for (no planets worth readying, no threat to hold off); Sol holds Trade (5),
// worth far more to it.
class DatahubRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.nekro.addTech("qdn");
        test.nekro.setTg(6);
        test.nekro.setStrategicCC(3);
        test.nekro.setTacticalCC(3);
        test.nekro.addSC(2);
        test.sol.addSC(5);
        test.isActive(test.sol, "action");
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiPrompt offer() {
        return AiTestGame.withContent(
                prompt("qdn", PromptSource.PUBLIC, NOW, "startQDN", "deleteButtons"),
                test.nekro.getRepresentationUnfogged() + ", you have the opportunity to use _Quantum Datahub Node_.");
    }

    // Trade is worth more to the seat than Diplomacy by more than the strategy token, the 3 trade goods and what Sol
    // gains, so it uses the node and then picks the Trade-for-Diplomacy swap.
    @Test
    void swapsForACardWorthMoreThanTheCost() {
        assertThat(pressed(DatahubRules.next(test.context(offer())))).isEqualTo("startQDN");

        AiPrompt targets = prompt("targets", PromptSource.PUBLIC, NOW + 10, "swapSCs_sol_qdn_5_2");
        assertThat(pressed(DatahubRules.next(test.context(targets)))).isEqualTo("swapSCs_sol_qdn_5_2");
    }

    // Sol holding several cards first asks whose cards to look at; the seat names Sol, then takes the planned pair.
    @Test
    void picksThePlayerThenThePairWhenTheOtherPlayerHoldsTwoCards() {
        test.sol.addSC(6);
        assertThat(pressed(DatahubRules.next(test.context(offer())))).isEqualTo("startQDN");

        AiPrompt players = prompt("players", PromptSource.PUBLIC, NOW + 10, "selectBeforeSwapSCs_sol_qdn");
        assertThat(pressed(DatahubRules.next(test.context(players)))).isEqualTo("selectBeforeSwapSCs_sol_qdn");

        AiPrompt pairs = prompt("pairs", PromptSource.PUBLIC, NOW + 20, "swapSCs_sol_qdn_5_2", "swapSCs_sol_qdn_6_2");
        assertThat(pressed(DatahubRules.next(test.context(pairs)))).isEqualTo("swapSCs_sol_qdn_5_2");
    }

    // Imperial is never handed to the rival with the most points, nor any card that is worth more to them than the
    // one they give up. Once another rival is further ahead, the same swaps with Sol are judged on value alone.
    @Test
    void neverHandsThePointsLeaderACardThatHelpsThemScore() {
        test.points(test.sol, 3);

        assertThat(DatahubRules.helpsLeaderScore(test.game, test.nekro, test.sol, 5, 8))
                .isTrue();
        assertThat(DatahubRules.helpsLeaderScore(test.game, test.nekro, test.sol, 6, 5))
                .isTrue();
        assertThat(DatahubRules.helpsLeaderScore(test.game, test.nekro, test.sol, 5, 6))
                .isFalse();

        test.points(test.addSeat("200000000000000002", "xxcha", "green"), 4);
        assertThat(DatahubRules.helpsLeaderScore(test.game, test.nekro, test.sol, 5, 8))
                .isFalse();
    }

    // Spending 3 of its 5 trade goods would leave too few for Negotiate Trade Routes, so the seat declines.
    @Test
    void keepsTheTradeGoodsItNeedsToScore() {
        test.nekro.setTg(5);
        test.game.getRevealedPublicObjectives().put("trade_routes", 1);

        assertThat(pressed(DatahubRules.next(test.context(offer())))).isEqualTo("deleteButtons");
    }

    // A swap between two cards worth about the same to the seat does not pay for the token and the trade goods.
    @Test
    void declinesASwapThatIsNotWorthTheCost() {
        test.nekro.getSCs().clear();
        test.nekro.addSC(1);
        test.sol.getSCs().clear();
        test.sol.addSC(7);

        assertThat(pressed(DatahubRules.next(test.context(offer())))).isEqualTo("deleteButtons");
    }
}
