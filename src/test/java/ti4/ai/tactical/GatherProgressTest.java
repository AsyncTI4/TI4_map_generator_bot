package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

// A gathering for a neutral Fracture planet that cannot win yet earns a share of the prize that grows smoothly with the
// space-win chance it and its next-round joiners would have, up to half the prize at the win gate.
class GatherProgressTest {

    private static final double GATE = 0.5;

    // A gathering that cannot win yet still earns a little (user ruling 2026-11-21): every ship it adds raises the
    // chance
    // of the strike it builds toward, so only a gathering with no chance at all earns nothing.
    @Test
    void onlyAGatheringWithNoChanceEarnsNothing() {
        assertThat(GatherProgress.share(0.0, GATE)).isZero();
        assertThat(GatherProgress.share(0.05, GATE)).isPositive();
        assertThat(GatherProgress.share(0.15, GATE)).isGreaterThan(GatherProgress.share(0.05, GATE));
    }

    @Test
    void aGatheringAtTheGateEarnsHalfThePrize() {
        assertThat(GatherProgress.share(GATE, GATE)).isCloseTo(GatherProgress.SHARE_AT_GATE, within(1e-9));
        assertThat(GatherProgress.value(12, GATE, GATE)).isCloseTo(6, within(1e-9));
    }

    @Test
    void theShareGrowsWithTheWinChance() {
        double previous = -1;
        for (double win = 0.0; win <= GATE; win += 0.05) {
            double share = GatherProgress.share(win, GATE);
            assertThat(share).isGreaterThanOrEqualTo(previous);
            previous = share;
        }
        assertThat(GatherProgress.share(0.4, GATE)).isGreaterThan(GatherProgress.share(0.3, GATE));
    }

    // The share rises slowly near the floor, so a group far from winning earns little and the credit gathers pace as
    // the win chance approaches the gate.
    @Test
    void theShareRisesFasterNearTheGate() {
        double low = GatherProgress.share(0.25, GATE) - GatherProgress.share(0.2, GATE);
        double high = GatherProgress.share(0.5, GATE) - GatherProgress.share(0.45, GATE);

        assertThat(high).isGreaterThan(low);
    }

    @Test
    void theShareNeverExceedsTheGateShare() {
        assertThat(GatherProgress.share(0.95, GATE)).isCloseTo(GatherProgress.SHARE_AT_GATE, within(1e-9));
    }

    // Styx holds three infantry: a gathering with no ground forces cannot take it, and one with four or more carries
    // all it needs.
    @Test
    void theShareCountsOnlyTheGroundForcesThatCanTakeThePlanet() {
        assertThat(GatherProgress.landingShare(0, 3)).isZero();
        assertThat(GatherProgress.landingShare(2, 3)).isCloseTo(0.5, within(1e-9));
        assertThat(GatherProgress.landingShare(4, 3)).isEqualTo(1);
        assertThat(GatherProgress.landingShare(8, 3)).isEqualTo(1);
    }

    @Test
    void aNegativePrizeIsWorthNothing() {
        assertThat(GatherProgress.value(-3, 0.45, GATE)).isZero();
    }

    // For Styx the share follows the gathered fleet's cost as a part of the fleet that takes Styx, in a straight line,
    // so every ship added is worth the same step.
    @Test
    void theShareGrowsInStepWithTheFleetStrength() {
        double quarter = GatherProgress.share(0, GATE, 0.25);
        double half = GatherProgress.share(0, GATE, 0.5);

        assertThat(half).isCloseTo(2 * quarter, within(1e-9));
        assertThat(GatherProgress.share(0, GATE, 0.75)).isGreaterThan(half);
        assertThat(GatherProgress.share(0, GATE, 1.0))
                .isCloseTo(GatherProgress.SHARE_AT_GATE * GatherProgress.COST_PROXY, within(1e-9));
    }

    @Test
    void aFleetWithNoWinChanceStillEarnsForItsStrength() {
        assertThat(GatherProgress.share(0, GATE, 0.3)).isPositive();
        assertThat(GatherProgress.share(0, GATE, 0)).isZero();
    }

    @Test
    void theWinChanceStillCountsWhenItIsAheadOfTheStrength() {
        assertThat(GatherProgress.share(GATE, GATE, 0.1)).isCloseTo(GatherProgress.SHARE_AT_GATE, within(1e-9));
    }

    // Styx holds three infantry. Ships without cargo earn half the landing share their capacity would give, as long as
    // the seat has ground forces it could fetch (a dock to build them, or idle ones elsewhere).
    @Test
    void shipsWithoutCargoEarnHalfTheLandingWhenGroundForcesCanBeFetched() {
        assertThat(GatherProgress.laterLandingShare(4, 3, true)).isCloseTo(0.5, within(1e-9));
        assertThat(GatherProgress.laterLandingShare(2, 3, true)).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void shipsWithoutCargoEarnNothingWhenNothingCanBeFetched() {
        assertThat(GatherProgress.laterLandingShare(4, 3, false)).isZero();
        assertThat(GatherProgress.laterLandingShare(0, 3, true)).isZero();
    }
}
