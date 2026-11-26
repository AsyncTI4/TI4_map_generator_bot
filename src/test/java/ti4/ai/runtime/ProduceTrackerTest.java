package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class ProduceTrackerTest extends BaseTi4Test {

    // Placing units one press at a time grows the same build; the bot clearing the map (next production's buttons)
    // closes it, with the cost owed for what was placed: 2 infantry and 2 fighters cost 1 + 1.
    @Test
    void recordsABuildWhenTheProducedMapIsCleared() {
        AiTestGame test = new AiTestGame();
        ProduceTracker tracker = new ProduceTracker();
        test.sol.produceUnit("gf_301_planet");
        tracker.observe(test.game);
        test.sol.produceUnit("gf_301_planet");
        test.sol.produceUnit("ff_301_space");
        tracker.observe(test.game);
        test.sol.produceUnit("ff_301_space");
        tracker.observe(test.game);
        assertThat(tracker.builds()).isEmpty();

        test.sol.resetProducedUnits();
        tracker.observe(test.game);

        assertThat(tracker.builds()).singleElement().satisfies(build -> {
            assertThat(build.faction()).isEqualTo("sol");
            assertThat(build.round()).isEqualTo(2);
            assertThat(build.cost()).isEqualTo(2);
            assertThat(build.units()).containsExactlyInAnyOrderEntriesOf(Map.of("gf", 2, "ff", 2));
        });
    }

    // A new production that does not contain the last one closes the last one even without an empty map in between.
    @Test
    void aDifferentMapClosesThePreviousBuild() {
        AiTestGame test = new AiTestGame();
        ProduceTracker tracker = new ProduceTracker();
        test.sol.produceUnit("gf_301_planet");
        tracker.observe(test.game);
        test.sol.resetProducedUnits();
        test.sol.produceUnit("ff_302_space");
        tracker.observe(test.game);

        assertThat(tracker.builds())
                .singleElement()
                .satisfies(build -> assertThat(build.units()).containsExactlyEntriesOf(Map.of("gf", 1)));
    }

    @Test
    void growingNeedsEveryPlacedUnitStillThere() {
        assertThat(ProduceTracker.grew(Map.of("gf_1_p", 1), Map.of("gf_1_p", 2, "ff_1_space", 1)))
                .isTrue();
        assertThat(ProduceTracker.grew(Map.of("gf_1_p", 2), Map.of("gf_1_p", 1)))
                .isFalse();
        assertThat(ProduceTracker.grew(Map.of("gf_1_p", 1), Map.of())).isFalse();
        assertThat(ProduceTracker.grew(Map.of("gf_1_p", 1), Map.of("ff_1_space", 1)))
                .isFalse();
    }

    @Test
    void unitTypesMergeAcrossTilesAndHolders() {
        assertThat(ProduceTracker.byUnitType(Map.of("gf_301_planeta", 2, "gf_301_planetb", 1, "dd_301_space", 1)))
                .containsExactlyEntriesOf(new java.util.TreeMap<>(Map.of("dd", 1, "gf", 3)));
    }

    @Test
    void seatLineGroupsBuildsByRound() {
        ProduceTracker.SeatBuilds xxcha = new ProduceTracker.SeatBuilds(
                "xxcha",
                4,
                List.of(
                        new ProduceTracker.Build("xxcha", 1, 3, Map.of("dd", 1, "gf", 2)),
                        new ProduceTracker.Build("xxcha", 1, 2, Map.of("gf", 2)),
                        new ProduceTracker.Build("xxcha", 3, 6, Map.of("ca", 3))));
        ProduceTracker.SeatBuilds sol = new ProduceTracker.SeatBuilds("sol", 4, List.of());

        assertThat(ProduceTracker.seatLines("g1", List.of(xxcha, sol)))
                .containsExactly(
                        "SEATPROD g1 xxcha: r1 cost 5 {dd=1, gf=4}; r3 cost 6 {ca=3}; total cost 11 in 3 builds over 4"
                                + " rounds",
                        "SEATPROD g1 sol: nothing built; total cost 0 in 0 builds over 4 rounds");
    }

    // Averages are per seat round: two xxcha seats over 4 + 6 rounds that spent 10 + 10 average 2.0 per round.
    @Test
    void batchAveragesPerSeatRound() {
        List<ProduceTracker.SeatBuilds> seats = List.of(
                new ProduceTracker.SeatBuilds(
                        "xxcha", 4, List.of(new ProduceTracker.Build("xxcha", 1, 10, Map.of("gf", 5)))),
                new ProduceTracker.SeatBuilds(
                        "xxcha", 6, List.of(new ProduceTracker.Build("xxcha", 2, 10, Map.of("dd", 2)))),
                new ProduceTracker.SeatBuilds("sol", 5, List.of()));

        assertThat(ProduceTracker.batchLines(seats))
                .containsExactly(
                        "Production per seat per round by faction (cost owed, units by type):",
                        "  sol: cost 0.0, units [] (1 seats, 5 seat rounds)",
                        "  xxcha: cost 2.0, units [dd 0.2, gf 0.5] (2 seats, 10 seat rounds)");
    }
}
