package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Saving an action secret means not going out of the way to meet its condition early; a combat that has already met it
// is scored. The question here is whether to pursue the condition now. Human tables usually finish by the status phase
// of round 5, so the seat pursues from the likely last round on (half the goal) and as soon as anyone is within four
// of the goal. Before that it is patient while it expects to meet the condition in a later round anyway:
// P = 1 - (1 - r)^(rounds before the end + 1) of at least 0.4, where the extra round is the last one, r is the
// secret's per-round chance, capped at its ordinary rate (an enemy a move away is not counted on to stay), times 0.85
// for each other combat secret in hand (they compete for the same combats). Patience can be switched off. Every
// scenario is a ten point game in round 2 with three dreadnoughts a move from an enemy destroyer, and Demonstrate Your
// Power in hand, unless a test says otherwise.
class ActionSecretTimingTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String savedProperty;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(AiSettings.SECRET_HOLD_PROPERTY);
        System.clearProperty(AiSettings.SECRET_HOLD_PROPERTY);
        test = new AiTestGame();
        home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        Tile enemy = test.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(enemy, "space", test.sol, UnitType.Destroyer, 1);
        test.nekro.setSecret("dyp");
    }

    @AfterEach
    void restoreProperty() {
        if (savedProperty == null) System.clearProperty(AiSettings.SECRET_HOLD_PROPERTY);
        else System.setProperty(AiSettings.SECRET_HOLD_PROPERTY, savedProperty);
    }

    private String otherNeighbourOfHome() {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !AiTestGame.neighbourOf(AiTestGame.HOME).equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    private boolean pursues(String secret) {
        return ActionSecretTiming.pursueNow(test.game, test.nekro, secret);
    }

    // Make an Example of Their World and Destroy Their Greatest Ship are never saved for later: in round 1, with
    // nobody scoring and plenty of rounds left, the seat still pursues them while it stays patient with dyp.
    @Test
    void neverSavesMakeAnExampleOrDestroyTheirGreatestShip() {
        test.game.setRound(1);
        test.nekro.setSecret("mew");
        test.nekro.setSecret("dtgs");

        assertThat(pursues("mew")).isTrue();
        assertThat(pursues("dtgs")).isTrue();
        assertThat(pursues("dyp")).isFalse();
    }

    // With an enemy a move away the chance of the condition is 0.6 a round, but only the ordinary 0.3 is counted on.
    // Round 1, nobody scoring: three rounds before the end, so four chances: 1 - 0.7^4 = 0.76. Round 3, the leader on
    // 4: the leader needs one more round to get to 6, so two chances: 1 - 0.7^2 = 0.51. Round 4, the leader on 5: one
    // round until round 5, two chances again: 0.51. All above 0.4, so the seat is patient.
    @Test
    void staysPatientInTheEarlyRoundsWhileEveryoneIsUnderSix() {
        test.game.setRound(1);
        assertThat(pursues("dyp")).isFalse();

        test.game.setRound(3);
        test.points(test.sol, 4);
        assertThat(pursues("dyp")).isFalse();

        test.game.setRound(4);
        test.points(test.sol, 1);
        assertThat(pursues("dyp")).isFalse();
    }

    // The end is in sight as soon as any player is on 6 of 10, an opponent or the seat itself.
    @Test
    void pursuesOnceAnyPlayerIsWithinFourOfTheGoal() {
        test.game.setRound(3);
        test.points(test.sol, 5);
        assertThat(pursues("dyp")).isFalse();

        test.points(test.sol, 1);
        assertThat(pursues("dyp")).isTrue();
    }

    @Test
    void pursuesOnceTheSeatItselfIsWithinFourOfTheGoal() {
        test.game.setRound(3);
        test.points(test.nekro, 6);

        assertThat(pursues("dyp")).isTrue();
    }

    // Round 5 is the likely last round of a ten point game, whatever the scores.
    @Test
    void pursuesFromTheLikelyLastRoundWithLowScores() {
        test.game.setRound(4);
        assertThat(pursues("dyp")).isFalse();

        test.game.setRound(5);
        assertThat(pursues("dyp")).isTrue();
    }

    // The last round is half the goal: round 7 and 10 points at fourteen. In round 6 with the leader on 9 the seat
    // has one round until round 7 and two chances: 0.51.
    @Test
    void theLateRoundScalesWithTheGoal() {
        test.game.setVp(14);
        test.game.setRound(6);
        test.points(test.sol, 9);
        assertThat(pursues("dyp")).isFalse();

        test.game.setRound(7);
        assertThat(pursues("dyp")).isTrue();

        test.game.setRound(6);
        test.points(test.sol, 1);
        assertThat(pursues("dyp")).isTrue();
    }

    // Destroy Their Greatest Ship with no war sun or flagship in sight is 0.03 a round (0.03 * 0.85 with Demonstrate
    // Your Power in hand): even four chances are 1 - 0.9745^4 = 0.10. A rare secret is pursued at once.
    @Test
    void pursuesARareSecretEarly() {
        test.game.setRound(1);
        test.nekro.setSecret("dtgs");

        assertThat(pursues("dtgs")).isTrue();
    }

    // Brave the Void with an enemy sitting in a reachable anomaly would be 0.6 a round, but only its ordinary 0.18 is
    // counted on. In round 3 with the leader on 4 (two chances, and 0.85 for Demonstrate Your Power in hand): 0.153 a
    // round is 1 - 0.847^2 = 0.28, below 0.4, where counting the 0.6 would give 1 - 0.49^2 = 0.76 and keep it patient.
    @Test
    void doesNotCountOnAnEnemyStayingWithinReach() {
        test.game.setRound(3);
        test.points(test.sol, 4);
        Tile rift = test.place("41", otherNeighbourOfHome());
        test.units(rift, "space", test.sol, UnitType.Destroyer, 1);
        test.nekro.setSecret("btv");

        assertThat(ActionSecretOdds.perRound(test.game, test.nekro, "btv")).isEqualTo(0.60);
        assertThat(pursues("btv")).isTrue();
    }

    // Round 3, the leader on 4: two chances. Alone, 0.3 a round is 1 - 0.7^2 = 0.51. A status secret does not compete.
    // With Brave the Void in hand, 0.3 * 0.85 = 0.255 a round: 1 - 0.745^2 = 0.44, still patient. With Unveil Flagship
    // as well, 0.3 * 0.85^2 = 0.217 a round: 1 - 0.783^2 = 0.39, below 0.4, and it pursues.
    @Test
    void competingCombatSecretsMakeItPursueSoonerThanAloneItWould() {
        test.game.setRound(3);
        test.points(test.sol, 4);
        assertThat(pursues("dyp")).isFalse();

        test.nekro.setSecret("eh");
        assertThat(pursues("dyp")).isFalse();

        test.nekro.setSecret("btv");
        assertThat(pursues("dyp")).isFalse();

        test.nekro.setSecret("uf");
        assertThat(pursues("dyp")).isTrue();
    }

    @Test
    void pursuesAtOnceWhenPatienceIsSwitchedOff() {
        assertThat(pursues("dyp")).isFalse();

        System.setProperty(AiSettings.SECRET_HOLD_PROPERTY, "false");

        assertThat(pursues("dyp")).isTrue();
    }
}
