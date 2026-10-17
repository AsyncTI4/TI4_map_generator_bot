package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.CommandTokenValue;
import ti4.testUtils.BaseTi4Test;

class RiderPayoffTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
    }

    @Test
    void anImperialRiderIsAPointAndTheGameWhenItWinsTheGame() {
        assertThat(RiderPayoff.of(test.game, test.sol, RiderPayoff.IMPERIAL))
                .isEqualTo(ScoringStakes.scoreValue(test.game, test.sol, 1));

        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO("Nine", 9));
        assertThat(RiderPayoff.of(test.game, test.sol, RiderPayoff.IMPERIAL)).isEqualTo(ScoringStakes.WIN_VALUE);
    }

    @Test
    void aTradeRiderIsFiveTradeGoods() {
        assertThat(RiderPayoff.of(test.game, test.sol, RiderPayoff.TRADE)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void aPoliticsRiderIsThreeActionCardsAndTheSpeakerToken() {
        assertThat(RiderPayoff.of(test.game, test.sol, RiderPayoff.POLITICS))
                .isGreaterThan(3 * ActionCardPrior.AVERAGE_CARD);

        test.game.setSpeakerUserID(test.sol.getUserID());
        assertThat(RiderPayoff.of(test.game, test.sol, RiderPayoff.POLITICS))
                .isCloseTo(3 * ActionCardPrior.AVERAGE_CARD, within(1e-9));
    }

    @Test
    void aLeadershipRiderIsThreeCommandTokens() {
        assertThat(RiderPayoff.of(test.game, test.sol, RiderPayoff.LEADERSHIP))
                .isEqualTo(CommandTokenValue.tactic(test.game, test.sol, 3))
                .isPositive();
    }

    // A seat with Propagation gets command tokens in place of the research.
    @Test
    void aTechnologyRiderIsTheResearchForASeatThatResearchesAndTokensForOneThatCannot() {
        assertThat(RiderPayoff.of(test.game, test.sol, RiderPayoff.TECHNOLOGY)).isPositive();
        assertThat(RiderPayoff.of(test.game, test.nekro, RiderPayoff.TECHNOLOGY))
                .isEqualTo(CommandTokenValue.tactic(test.game, test.nekro, 3));
    }

    // What another seat's rider is worth to it is judged from its hand, which the AI does not look at.
    @Test
    void aTechnologyOrLeadershipRiderOfAnotherSeatIsOnlyEstimated() {
        double estimate = 2 * ActionCardPrior.AVERAGE_CARD;

        assertThat(RiderPayoff.of(test.game, test.nekro, test.sol, RiderPayoff.TECHNOLOGY))
                .isCloseTo(estimate, within(1e-9));
        assertThat(RiderPayoff.of(test.game, test.nekro, test.sol, RiderPayoff.LEADERSHIP))
                .isCloseTo(estimate, within(1e-9));
        assertThat(RiderPayoff.of(test.game, test.sol, test.sol, RiderPayoff.TECHNOLOGY))
                .isEqualTo(RiderPayoff.of(test.game, test.sol, RiderPayoff.TECHNOLOGY));
        assertThat(RiderPayoff.of(test.game, test.nekro, test.sol, RiderPayoff.IMPERIAL))
                .isEqualTo(RiderPayoff.of(test.game, test.sol, RiderPayoff.IMPERIAL));
    }

    @Test
    void anyOtherRiderIsWorthAboutTwoCards() {
        assertThat(RiderPayoff.of(test.game, test.sol, "Keleres Rider"))
                .isCloseTo(2 * ActionCardPrior.AVERAGE_CARD, within(1e-9));
    }
}
