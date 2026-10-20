package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// Trade's spend-objective value counts what holding the card brings by status (3 trade goods and 3 washed commodities)
// against what following another seat's Trade would bring anyway (the 3 washed commodities), instead of the primary's
// 3 trade goods alone.
class TradeSpendObjectiveTest extends BaseTi4Test {

    private AiTestGame test;

    // Nekro controls Mordai II (4 resources) and has 3 commodities to replenish; Erect a Monument takes 8 resources.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.isActive(test.nekro, "strategy");
        test.nekroHome();
        test.game.getRevealedPublicObjectives().put("monument", 1);
        test.nekro.setStrategicCC(1);
    }

    // 4 + 6 = 10 reaches 8 only by holding Trade; following gets 4 + 3 = 7. The primary's 3 trade goods alone
    // (4 + 3 = 7) would have valued nothing.
    @Test
    void holdingTradeUnlocksAMonumentFollowingCannot() {
        assertThat(TradeUses.spendPointsUnlocked(test.game, test.nekro)).isEqualTo(1);
    }

    // With 1 trade good, following already reaches 8, so holding Trade adds nothing for the objective.
    @Test
    void addsNothingWhenFollowingAlreadyUnlocksIt() {
        test.nekro.setTg(1);

        assertThat(TradeUses.spendPointsUnlocked(test.game, test.nekro)).isZero();
    }

    // Without a strategy token to follow, only holding Trade replenishes the commodities.
    @Test
    void creditsTheReplenishWhenTheSeatCannotFollow() {
        test.nekro.setTg(1);
        test.nekro.setStrategicCC(0);

        assertThat(TradeUses.spendPointsUnlocked(test.game, test.nekro)).isEqualTo(1);
    }
}
