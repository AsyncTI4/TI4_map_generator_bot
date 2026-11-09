package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.trade.TradeRules;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.testUtils.BaseTi4Test;

// Gift of Prescience wins games: holders keep it for the final round to act first and score before anyone else.
// The Naalu never gives it away for less than a victory point, whether or not the Custodians are on Mecatol Rex.
class GiftOfPrescienceTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Player naalu;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        naalu = test.addSeat(AiTestGame.SECOND_AI_ID, "naalu", "green");
        naalu.addOwnedPromissoryNoteByID(GiftOfPrescience.ALIAS);
        naalu.setPromissoryNote(GiftOfPrescience.ALIAS);
    }

    private double giveCost() {
        return TradeRules.noteGiveCost(test.game, naalu, test.sol, GiftOfPrescience.ALIAS);
    }

    @Test
    void costsAVictoryPointOnceTheCustodiansAreGone() {
        test.place("18", "000");

        assertThat(giveCost()).isCloseTo(ObjectiveValue.VICTORY_POINT_VALUE, within(EXACT));
    }

    @Test
    void costsAVictoryPointWhileTheCustodiansAreOnMecatolRex() {
        test.place("18", "000").addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");

        assertThat(giveCost()).isCloseTo(ObjectiveValue.VICTORY_POINT_VALUE, within(EXACT));
    }
}
