package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
}
