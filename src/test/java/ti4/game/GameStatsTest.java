package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.GameStats.ActionCardPlay;

class GameStatsTest {

    @Test
    void shouldCancelTheNewestUncanceledCopy() {
        GameStats stats = new GameStats();
        stats.recordAcPlay("Flank Speed", null);
        stats.recordAcPlay("Flank Speed", null);

        assertThat(stats.markLatestPlayCanceled("Flank Speed")).isTrue();
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::isCanceled)
                .containsExactly(false, true);

        // A second cancel falls back to the earlier copy, and then there is nothing left to cancel.
        assertThat(stats.markLatestPlayCanceled("Flank Speed")).isTrue();
        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::isCanceled)
                .containsExactly(true, true);
        assertThat(stats.markLatestPlayCanceled("Flank Speed")).isFalse();
        assertThat(stats.markLatestPlayCanceled("Parley")).isFalse();
    }

    @Test
    void shouldLeaveALaterCopyPlayableAfterAnEarlierOneIsCanceled() {
        GameStats stats = new GameStats();
        stats.recordAcPlay("Flank Speed", null);
        stats.markLatestPlayCanceled("Flank Speed");
        stats.recordAcPlay("Flank Speed", null);

        assertThat(stats.getActionCardPlays())
                .extracting(ActionCardPlay::isCanceled)
                .containsExactly(true, false);
        assertThat(stats.getTotalPlays("Flank Speed")).isEqualTo(2);
    }
}
