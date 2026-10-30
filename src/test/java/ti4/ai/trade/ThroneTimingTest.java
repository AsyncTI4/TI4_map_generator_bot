package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Humans rarely swap Support for the Throne before rounds 2-4: a swap in round 1 costs the point the moment the
 * neighbour grabs a system, so the AI waits until late in round 3, unless both slices are already claimed and there is
 * tension over territory. Nekro (black) and Sol (blue) are neighbouring seats.
 */
class ThroneTimingTest extends BaseTi4Test {

    private TradeTable table;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        nekro = table.nekro;
        sol = table.sol;
        table.nekroAndSolNeighbour();
        nekro.setTacticalCC(3);
    }

    @Test
    void roundOneWithoutTensionCostsTheSwap() {
        table.game.setRound(1);

        assertThat(ThroneTiming.open(table.game, nekro, sol)).isFalse();
        assertThat(ThroneValue.earlyCost(table.game, nekro, sol)).isPositive();
    }

    @Test
    void roundTwoStaysClosedWithoutTension() {
        table.game.setRound(2);

        assertThat(ThroneTiming.open(table.game, nekro, sol)).isFalse();
    }

    @Test
    void roundThreeOpensWhenTheSeatIsOutOfTactics() {
        table.game.setRound(3);
        assertThat(ThroneTiming.open(table.game, nekro, sol)).isFalse();

        nekro.setTacticalCC(1);

        assertThat(ThroneTiming.open(table.game, nekro, sol)).isTrue();
        assertThat(ThroneValue.earlyCost(table.game, nekro, sol)).isZero();
    }

    @Test
    void roundFourIsAlwaysOpen() {
        table.game.setRound(4);

        assertThat(ThroneTiming.open(table.game, nekro, sol)).isTrue();
    }

    // Both slices claimed (nothing left to take) and the partner sitting in the seat's system: the one early exception.
    @Test
    void anEarlySwapNeedsBothSlicesTakenAndTension() {
        table.game.setRound(1);
        table.presence(sol, TradeTable.NEKRO_SPOT);

        assertThat(ThroneTiming.tension(table.game, nekro, sol)).isTrue();
        assertThat(ThroneTiming.open(table.game, nekro, sol)).isTrue();
    }

    @Test
    void anUnclaimedPlanetInTheSliceKeepsTheWindowClosed() {
        table.game.setRound(1);
        table.solHome();
        table.test.place("25", TradeTable.BESIDE_SOL);
        table.presence(sol, TradeTable.NEKRO_SPOT);

        assertThat(ThroneTiming.sliceTaken(table.game, sol)).isFalse();
        assertThat(ThroneTiming.open(table.game, nekro, sol)).isFalse();

        sol.addPlanet("quann");
        table.changed();

        assertThat(ThroneTiming.sliceTaken(table.game, sol)).isTrue();
    }

    // An aggressive seat wants the swap with the table's strongest fleet; a passive seat wants its neighbour.
    @Test
    void aggressiveSeatsPreferTheStrongestFleetAndPassiveSeatsTheirNeighbour() {
        table.game.setRound(4);
        Player far = table.humanSeat("letnev", "red");
        table.presence(far, TradeTable.FAR_SPOT);
        table.test.units(
                table.game.getTileByPosition(TradeTable.FAR_SPOT),
                "space",
                far,
                ti4.helpers.Units.UnitType.Dreadnought,
                3);
        table.changed();

        assertThat(ThroneValue.fit(table.game, nekro, far)).isGreaterThan(ThroneValue.fit(table.game, nekro, sol));
        assertThat(ThroneValue.fit(table.game, sol, "sol".equals(sol.getFaction()) ? nekro : sol))
                .isEqualTo(ThroneValue.fit(table.game, sol, nekro));
    }
}
