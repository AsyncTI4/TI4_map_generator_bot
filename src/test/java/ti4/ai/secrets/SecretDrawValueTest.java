package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ScoringStakes;
import ti4.testUtils.BaseTi4Test;

class SecretDrawValueTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        test.game.setVp(10);
        test.game.setRound(2);
    }

    private void leaderAt(int points) {
        String award = "Test points " + points;
        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO(award, points));
    }

    // With the game far from over, the secret will be scored sooner or later: it is worth a point.
    @Test
    void aFreshSecretEarlyInTheGameIsWorthAboutAPoint() {
        double point = ScoringStakes.scoreValue(test.game, test.nekro, 1);

        assertThat(SecretDrawValue.of(test.game, test.nekro)).isCloseTo(point, within(EXACT));
        assertThat(SecretDrawValue.of(test.game, test.nekro)).isGreaterThan(3.0);
    }

    // Two rounds from the end a secret has less time to be scored, so only the share ready on the board counts.
    @Test
    void aSecretIsWorthLessAsTheGameNearsItsEnd() {
        double early = SecretDrawValue.of(test.game, test.nekro);
        leaderAt(9);

        double late = SecretDrawValue.of(test.game, test.nekro);

        assertThat(SecretDrawValue.roundsLeft(test.game)).isEqualTo(1);
        assertThat(late).isLessThan(early);
        assertThat(late).isPositive();
    }

    @Test
    void theRoundsLeftAreTheLeadersPointsToGoOverTwoAPoint() {
        assertThat(SecretDrawValue.roundsLeft(test.game)).isEqualTo(5);

        leaderAt(6);

        assertThat(SecretDrawValue.roundsLeft(test.game)).isEqualTo(2);
    }

    // Only one secret scores in each status phase, so a hand that already fills the rounds left leaves no room.
    @Test
    void aSecretIsWorthNothingWhenTheSecretsInHandFillTheRoundsLeft() {
        leaderAt(7);
        test.nekro.setSecret("dhw");
        test.nekro.setSecret("gamf");

        assertThat(SecretDrawValue.roundsLeft(test.game)).isEqualTo(2);
        assertThat(SecretDrawValue.of(test.game, test.nekro)).isZero();
    }

    // Two secrets scored and the third already scorable: a draw would only replace a secret worth keeping.
    @Test
    void aFullHandWorthKeepingMakesADrawWorthTheSwapAtMost() {
        test.nekro.setSecretScored("dhw");
        test.nekro.setSecretScored("gamf");
        test.game.addLaw("conventions", null);
        test.game.addLaw("minister_war", "sol");
        test.game.addLaw("shard_of_the_throne", "sol");
        test.nekro.setSecret("dp");
        assertThat(SecretDrawValue.hasRoom(test.nekro)).isFalse();

        assertThat(SecretDrawValue.of(test.game, test.nekro)).isLessThanOrEqualTo(SecretDrawValue.SWAP_VALUE);
    }

    @Test
    void theReadinessIsTheMeanOverTheSecretsNobodyHasScored() {
        double all = SecretDrawValue.unseenReadiness(test.game, test.nekro);

        assertThat(all).isBetween(0.0, 1.0);
        assertThat(all).isGreaterThan(0);
    }

    // Another seat's secrets are never read: the deck the seat draws from is the deck less what has been scored.
    @Test
    void aScoredSecretLeavesThePoolOtherPlayersMayHold() {
        int pool = SecretOdds.unseenPool(test.game, test.nekro).size();
        test.sol.setSecret("dhw");
        test.sol.setSecretScored("dhw");

        assertThat(SecretOdds.unseenPool(test.game, test.nekro)).hasSize(pool - 1);
    }
}
