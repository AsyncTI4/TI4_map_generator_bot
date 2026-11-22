package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;

class ContinuationAbTest {

    private static final Set<String> TREATED = Set.of("sol", "hacan", "xxcha");

    private static ContinuationAb.Arm arm(Set<String> treated, Map<String, Integer> gains) {
        return new ContinuationAb.Arm(treated, gains);
    }

    @Test
    void treatedHalfIsHalfTheSeatsInSeatOrder() {
        List<Integer> half = ContinuationAb.treatedHalf(6, new Random(3));

        assertThat(half).hasSize(3).isSorted().allMatch(index -> index >= 0 && index < 6);
        assertThat(ContinuationAb.treatedHalf(6, new Random(3))).isEqualTo(half);
    }

    @Test
    void differenceIsTreatedAverageMinusControlAverage() {
        Map<String, Integer> gains = Map.of("sol", 3, "hacan", 1, "xxcha", 2, "nekro", 0, "letnev", 1, "sardakk", 2);

        assertThat(ContinuationAb.difference(gains, TREATED)).isCloseTo(2.0 - 1.0, within(1e-9));
    }

    @Test
    void gainsSubtractThePointsAtTheSnapshot() {
        assertThat(ContinuationAb.gains(Map.of("sol", 2, "hacan", 1), Map.of("sol", 4, "hacan", 1)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("sol", 2, "hacan", 0));
    }

    // When the experiments change nothing, both arms play the same game: the arms' differences cancel exactly.
    @Test
    void aPairWhereNothingChangedIsUntouchedAndWorthZero() {
        Map<String, Integer> gains = Map.of("sol", 2, "hacan", 0, "xxcha", 1, "nekro", 1, "letnev", 0, "sardakk", 1);
        Set<String> others = Set.of("nekro", "letnev", "sardakk");
        ContinuationAb.Pair pair = new ContinuationAb.Pair(1, 2, 0, arm(TREATED, gains), arm(others, gains));

        assertThat(pair.untouched()).isTrue();
        assertThat(pair.value()).isCloseTo(0, within(1e-9));
    }

    // Treatment gains 1 VP more than control in both arms, whichever half it is.
    @Test
    void aPairAveragesBothArms() {
        Set<String> others = Set.of("nekro", "letnev", "sardakk");
        ContinuationAb.Arm a =
                arm(TREATED, Map.of("sol", 2, "hacan", 2, "xxcha", 2, "nekro", 1, "letnev", 1, "sardakk", 1));
        ContinuationAb.Arm b =
                arm(others, Map.of("sol", 1, "hacan", 1, "xxcha", 1, "nekro", 2, "letnev", 2, "sardakk", 2));

        assertThat(new ContinuationAb.Pair(1, 2, 0, a, b).value()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void summaryCountsSnapshotsGamesAndUntouchedPairs() {
        Map<String, Integer> even = Map.of("sol", 1, "hacan", 1, "xxcha", 1, "nekro", 1, "letnev", 1, "sardakk", 1);
        Set<String> others = Set.of("nekro", "letnev", "sardakk");
        List<ContinuationAb.Pair> pairs = List.of(
                new ContinuationAb.Pair(1, 2, 0, arm(TREATED, even), arm(others, even)),
                new ContinuationAb.Pair(1, 3, 0, arm(TREATED, even), arm(others, even)),
                new ContinuationAb.Pair(2, 2, 0, arm(TREATED, even), arm(others, even)));

        assertThat(ContinuationAb.summary(pairs).getFirst())
                .isEqualTo("pairs 3, snapshots 3, games 2; untouched pairs 3 (100%)");
    }

    @Test
    void flagsParseNamesAllAndNone() {
        assertThat(ContinuationAb.parseFlags("none")).isEmpty();
        assertThat(ContinuationAb.parseFlags(null)).isEmpty();
        assertThat(ContinuationAb.parseFlags("win_threat_feasible, BOAT_FLOAT"))
                .containsExactlyInAnyOrder(
                        AiExperiments.Experiment.WIN_THREAT_FEASIBLE, AiExperiments.Experiment.BOAT_FLOAT);
        assertThat(ContinuationAb.parseFlags("all")).hasSize(AiExperiments.Experiment.values().length);
    }
}
