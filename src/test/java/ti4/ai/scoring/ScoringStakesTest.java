package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ScoringStakesTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private int awards;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(3);
        test.aiIsActive("action");
        test.nekroHome();
        test.game.addCustomPO("Custodians", 0);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(player.getUserID(), test.game.addCustomPO("Test points " + (++awards), points));
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    // A point is worth 6 trade goods to start with; a score that reaches the goal wins the game.
    @Test
    void aScoreThatReachesTheGoalIsWorthTheWholeGame() {
        points(test.nekro, 9);

        assertThat(ScoringStakes.scoreValue(test.game, test.nekro, 1))
                .isCloseTo(ScoringStakes.WIN_VALUE, within(EXACT));
        assertThat(ScoringStakes.scoreValue(test.game, test.sol, 1)).isCloseTo(8, within(EXACT));
        assertThat(ScoringStakes.scoreValue(test.game, test.sol, 0.5)).isCloseTo(4, within(EXACT));
        assertThat(ScoringStakes.scoreValue(test.game, test.sol, 0)).isZero();
    }

    // A rival's points count in full only when they would win; otherwise a quarter of what they are worth to them.
    @Test
    void aRivalsPointsCountInFullOnlyWhenTheyWouldWin() {
        points(test.sol, 5);

        double worth = ScoringStakes.scoreValue(test.game, test.sol, 1);
        assertThat(ScoringStakes.rivalStake(test.game, test.sol, 1)).isCloseTo(0.25 * worth, within(EXACT));

        points(test.sol, 4);
        assertThat(ScoringStakes.rivalStake(test.game, test.sol, 1)).isCloseTo(ScoringStakes.WIN_VALUE, within(EXACT));
    }

    // Negotiate Trade Routes (5 trade goods) is the one point Nekro qualifies for.
    @Test
    void pointsInReachAreThePublicsAPlayerQualifiesForLimitedByItsScoringChances() {
        reveal("trade_routes");
        test.nekro.setTg(5);

        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.nekro))
                .isCloseTo(1, within(EXACT));
        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.sol)).isZero();
    }

    // Imperial's holder scores one more public, and a point for Mecatol Rex.
    @Test
    void anUnplayedImperialWithMecatolRexAddsAPoint() {
        test.place("18", "000");
        test.nekro.addPlanet("mr");
        test.nekro.addSC(IMPERIAL);

        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.nekro))
                .isCloseTo(1, within(EXACT));

        test.game.setSCPlayed(IMPERIAL, true);
        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.nekro))
                .isZero();
    }

    // The number of secret objectives in hand is public; the chance one is ready comes from card counting.
    @Test
    void anUnscoredSecretAddsItsChanceOfBeingReady() {
        test.sol.setSecret("dhw");

        double reach = ScoringStakes.pointsInReach(test.game, test.nekro, test.sol);

        assertThat(reach).isGreaterThan(0).isLessThanOrEqualTo(1);
    }

    @Test
    void pointsInReachNeverLooksInsideAPlayersHand() {
        test.sol.setSecret("dhw");
        test.sol.setActionCard("sabo1", 1);
        test.sol.setPromissoryNote("blue_ta", 17);
        Player spy = Mockito.spy(test.sol);
        test.game.getPlayers().put(spy.getUserID(), spy);

        ScoringStakes.pointsInReach(test.game, test.nekro, spy);
        ScoringStakes.contender(test.game, test.nekro, spy);
        ScoringStakes.anyOpponentIsContender(test.game, test.nekro);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }

    @Test
    void aPlayerWithinTwoPointsOfTheGoalIsAContender() {
        assertThat(ScoringStakes.contender(test.game, test.nekro, test.sol)).isFalse();
        assertThat(ScoringStakes.anyOpponentIsContender(test.game, test.nekro)).isFalse();

        points(test.sol, 8);

        assertThat(ScoringStakes.contender(test.game, test.nekro, test.sol)).isTrue();
        assertThat(ScoringStakes.anyOpponentIsContender(test.game, test.nekro)).isTrue();
        assertThat(ScoringStakes.anyOpponentIsContender(test.game, test.sol)).isFalse();
    }
}
