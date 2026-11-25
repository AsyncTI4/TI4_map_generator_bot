package ti4.ai.nekro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Seats open with a small fleet pool and are pulled toward a bigger one as the game goes on: one fleet token out of
// the round one status phase is fine; a round two token the turns have no use for (Leadership is the usual source)
// makes a second fleet token, then a third; from round three spare tokens fill the pool to three, then four.
class FleetTokensByRoundTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;

    private AiTestGame test;
    private Player seat;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        seat = test.nekro;
        for (Player other : test.game.getRealPlayers()) {
            if (other != seat) other.setTacticalCC(0);
        }
        test.units(test.nekroHome(), "space", seat, UnitType.Carrier, 1);
    }

    @Test
    void theRoundOneStatusPhaseAddsNoFleetForTheRound() {
        assertThat(CommandTokenPolicy.fleetEnteringRound(2, false)).isZero();
        assertThat(CommandTokenPolicy.fleetEnteringRound(2, true)).isZero();
    }

    @Test
    void theFleetTargetGrowsEachRound() {
        assertThat(CommandTokenPolicy.roundFleet(1)).isZero();
        assertThat(CommandTokenPolicy.roundFleet(2)).isEqualTo(2);
        assertThat(CommandTokenPolicy.roundFleetStretch(2)).isEqualTo(3);
        assertThat(CommandTokenPolicy.roundFleet(3)).isEqualTo(3);
        assertThat(CommandTokenPolicy.roundFleetStretch(3)).isEqualTo(4);
        assertThat(CommandTokenPolicy.fleetEnteringRound(3, false)).isEqualTo(3);
        assertThat(CommandTokenPolicy.fleetEnteringRound(3, true)).isEqualTo(4);
    }

    // A Leadership token in round two that no follow or activation needs goes to a one-token fleet pool.
    @Test
    void aSpareRoundTwoTokenGrowsAOneTokenFleet() {
        test.game.setPhaseOfGame("action");
        test.game.setRound(2);
        tokens(3, 1, 2);
        assertThat(ti4.ai.strategy.TokenPlan.of(test.game, seat).strategy()).isLessThanOrEqualTo(2);

        assertThat(CommandTokenPolicy.poolToGrow(test.game, seat)).isEqualTo("increase_fleet_cc");
    }

    // A strategy card the seat means to follow still has no token, so the round two token goes there first.
    @Test
    void aRoundTwoTokenWithAFollowToPayForSkipsTheFleet() {
        test.game.setPhaseOfGame("action");
        test.game.setRound(2);
        tokens(3, 1, 0);
        test.sol.addSC(TECHNOLOGY);
        int wanted = ti4.ai.strategy.TokenPlan.of(test.game, seat).strategy();
        assertThat(wanted).isPositive();

        assertThat(CommandTokenPolicy.poolToGrow(test.game, seat)).isEqualTo("increase_strategy_cc");
    }

    // The same seat in round one keeps its tokens for its turns.
    @Test
    void aRoundOneTokenLeavesTheFleetAlone() {
        test.game.setPhaseOfGame("action");
        test.game.setRound(1);
        tokens(3, 1, 2);

        assertThat(CommandTokenPolicy.poolToGrow(test.game, seat)).isNotEqualTo("increase_fleet_cc");
    }

    @Test
    void roundThreeTokensGrowATwoTokenFleet() {
        test.game.setPhaseOfGame("action");
        test.game.setRound(3);
        tokens(3, 2, 2);

        assertThat(CommandTokenPolicy.poolToGrow(test.game, seat)).isEqualTo("increase_fleet_cc");
    }

    // With tokens to spare the round three split reaches four fleet tokens; a tight one still keeps three.
    @Test
    void theRoundThreeSplitHoldsThreeToFourFleetTokens() {
        test.game.setPhaseOfGame("action");
        test.game.setRound(3);

        assertThat(CommandTokenPolicy.ideal(test.game, seat, 4)).containsEntry(CommandTokenPolicy.Pool.FLEET, 3);
        assertThat(CommandTokenPolicy.ideal(test.game, seat, 14).get(CommandTokenPolicy.Pool.FLEET))
                .isGreaterThanOrEqualTo(4);
    }

    // The round two status phase plans for round three, so a seat redistributes into three fleet tokens or more.
    @Test
    void theRoundTwoStatusPhasePlansThreeFleetTokens() {
        test.game.setPhaseOfGame("statusHomework");
        test.game.setRound(2);

        assertThat(CommandTokenPolicy.ideal(test.game, seat, 10).get(CommandTokenPolicy.Pool.FLEET))
                .isGreaterThanOrEqualTo(3);
    }

    // Out of the round one status phase a one-ship seat keeps only the two spare fleet tokens it always kept.
    @Test
    void theRoundOneStatusPhaseAddsNothingBeyondTheSpareFleet() {
        test.game.setPhaseOfGame("statusHomework");
        test.game.setRound(1);

        assertThat(CommandTokenPolicy.ideal(test.game, seat, 10)).containsEntry(CommandTokenPolicy.Pool.FLEET, 2);
    }

    private void tokens(int tactic, int fleet, int strategy) {
        seat.setTacticalCC(tactic);
        seat.setFleetCC(fleet);
        seat.setStrategicCC(strategy);
    }
}
