package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AbSummaryTest {
    private AbSummary.GameResult game(int slot, SelfPlayGame.Ending ending) {
        return new AbSummary.GameResult(slot, 42, ending.name(), 5, 8, 7, 1, ending.finished());
    }

    private AbSummary.GameResult roundLimited(int slot, SelfPlayGame.Ending ending) {
        boolean complete = ending == SelfPlayGame.Ending.STOP_CONDITION || ending.finished();
        return new AbSummary.GameResult(slot, 42, ending.name(), 2, 3, 2, 1, complete);
    }

    @Test
    void onlyPairsOfFinishedGamesContributeToStrengthEstimates() {
        var won = game(0, SelfPlayGame.Ending.VICTORY_POINTS);
        assertThat(AbSummary.completedPair(List.of(won, game(1, SelfPlayGame.Ending.OBJECTIVES_EXHAUSTED))))
                .isTrue();
        assertThat(AbSummary.completedPair(List.of(won))).isFalse();
        for (var ending : SelfPlayGame.Ending.values()) {
            if (ending == SelfPlayGame.Ending.VICTORY_POINTS || ending == SelfPlayGame.Ending.OBJECTIVES_EXHAUSTED)
                continue;
            assertThat(AbSummary.completedPair(List.of(won, game(1, ending))))
                    .as("An unfinished game is not a win: %s", ending)
                    .isFalse();
            assertThat(game(1, ending).treatmentWinShare()).isNaN();
        }
    }

    // RoundTwoSelfPlayTest stops every game after its last round, so they all end STOP_CONDITION. Those pairs used to
    // be thrown away as unfinished, leaving its A/B point estimates always empty.
    @Test
    void aRoundLimitedBatchCountsGamesItStoppedOnPurpose() {
        var stopped = roundLimited(0, SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(AbSummary.completedPair(List.of(stopped, roundLimited(1, SelfPlayGame.Ending.STOP_CONDITION))))
                .isTrue();
        assertThat(stopped.treatmentWinShare()).isEqualTo(1);
        for (var ending : List.of(
                SelfPlayGame.Ending.LIMIT,
                SelfPlayGame.Ending.STALLED,
                SelfPlayGame.Ending.LOOPED,
                SelfPlayGame.Ending.LOST_GAME)) {
            assertThat(AbSummary.completedPair(List.of(stopped, roundLimited(1, ending))))
                    .as("A game the harness had to cut short is not a result: %s", ending)
                    .isFalse();
        }
    }

    private static AbSummary.Seating seating(long seed, Map<String, Integer> points, Set<String> control) {
        return new AbSummary.Seating(seed, true, points, control);
    }

    // With fixed seating Xxcha is treatment in one game of the pair and control in the other, so its own paired
    // difference is its points as treatment minus its points as control: (9 - 6) and (8 - 7) here.
    @Test
    void reportsEachFactionsOwnTreatmentMinusControlPoints() {
        var lines = AbSummary.perFaction(List.of(
                seating(1, Map.of("xxcha", 9, "sol", 7), Set.of("sol")),
                seating(1, Map.of("xxcha", 6, "sol", 10), Set.of("xxcha")),
                seating(2, Map.of("xxcha", 8, "sol", 5), Set.of("sol")),
                seating(2, Map.of("xxcha", 7, "sol", 6), Set.of("xxcha"))));

        assertThat(lines)
                .anySatisfy(line -> assertThat(line)
                        .startsWith("A/B faction xxcha: treatment minus control victory points 2.000 ± 1.000")
                        .contains("over 2 pairs", "same side in both games 0"));
        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith("A/B faction sol:"));
    }

    // A faction pool that rotated the seating between the two games of a pair kept every faction on one side; such
    // pairs say nothing about the faction and are counted apart.
    @Test
    void leavesOutAFactionThatPlayedTheSameSideInBothGames() {
        var lines = AbSummary.perFaction(List.of(
                seating(1, Map.of("xxcha", 9, "sol", 7), Set.of("sol")),
                seating(1, Map.of("xxcha", 6, "sol", 10), Set.of("sol"))));

        assertThat(lines)
                .anySatisfy(line -> assertThat(line)
                        .startsWith("A/B faction xxcha: treatment minus control victory points n/a over 0 pairs")
                        .contains("same side in both games 1"));
    }

    @Test
    void skipsPairsWithAnUnfinishedGame() {
        var lines = AbSummary.perFaction(List.of(
                seating(1, Map.of("xxcha", 9), Set.of()),
                new AbSummary.Seating(1, false, Map.of("xxcha", 6), Set.of("xxcha"))));

        assertThat(lines).isEmpty();
    }
}
