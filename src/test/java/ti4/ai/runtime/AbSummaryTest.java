package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AbSummaryTest {
    private AbSummary.GameResult game(int slot, SelfPlayGame.Ending ending) {
        return new AbSummary.GameResult(slot, 42, ending.name(), 5, 8, 7, 1);
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
}
