package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class PromissoryTrackerTest extends BaseTi4Test {

    private static final String SOL_SFTT = "blue_sftt";
    private static final String SOL_CEASEFIRE = "blue_cf";

    private AiTestGame test;
    private PromissoryTracker tracker;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.sol.addOwnedPromissoryNoteByID(SOL_SFTT);
        test.sol.addOwnedPromissoryNoteByID(SOL_CEASEFIRE);
        test.sol.setPromissoryNote(SOL_SFTT);
        test.sol.setPromissoryNote(SOL_CEASEFIRE);
        tracker = new PromissoryTracker();
        tracker.observe(test.game);
    }

    private void give(String note) {
        test.sol.removePromissoryNote(note);
        test.nekro.setPromissoryNote(note);
    }

    // Support for the Throne goes straight into the receiver's play area: still a note its owner gave away.
    @Test
    void countsANoteGivenIntoThePlayAreaAsATransfer() {
        give(SOL_SFTT);
        test.nekro.addPromissoryNoteToPlayArea(SOL_SFTT);
        tracker.observe(test.game);

        PromissoryTracker.Summary summary = tracker.summary(test.game);
        assertThat(summary.transfers()).singleElement().satisfies(transfer -> {
            assertThat(transfer.from()).isEqualTo(test.sol.getFaction());
            assertThat(transfer.to()).isEqualTo(test.nekro.getFaction());
            assertThat(transfer.givenByOwner()).isTrue();
        });
        assertThat(summary.givenByOwners()).isEqualTo(1);
        assertThat(summary.neverLeftOwner())
                .contains(test.sol.getFaction() + "|Ceasefire")
                .doesNotContain(test.sol.getFaction() + "|Support for the Throne");
    }

    // A played note goes back to its owner: that is a play, not the note being traded back.
    @Test
    void countsANoteReturnedByPlayingItAsAPlayOnly() {
        give(SOL_CEASEFIRE);
        tracker.observe(test.game);
        tracker.played(test.game, test.nekro, SOL_CEASEFIRE);
        test.nekro.removePromissoryNote(SOL_CEASEFIRE);
        test.sol.setPromissoryNote(SOL_CEASEFIRE);
        tracker.observe(test.game);

        PromissoryTracker.Summary summary = tracker.summary(test.game);
        assertThat(summary.transfers()).hasSize(1);
        assertThat(summary.playedByOthers()).isEqualTo(1);
        assertThat(summary.receivedUnplayed()).isEmpty();
        assertThat(summary.neverLeftOwner())
                .contains(test.sol.getFaction() + "|Support for the Throne")
                .doesNotContain(test.sol.getFaction() + "|Ceasefire");
    }

    @Test
    void reportsAReceivedNoteNeverPlayed() {
        give(SOL_CEASEFIRE);

        PromissoryTracker.Summary summary = tracker.summary(test.game);
        assertThat(summary.receivedUnplayed()).containsExactly(test.nekro.getFaction() + "|Ceasefire");
        assertThat(summary.plays()).isEmpty();
    }
}
