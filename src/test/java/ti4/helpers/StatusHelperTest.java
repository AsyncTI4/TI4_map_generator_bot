package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupService;

class StatusHelperTest {

    @Test
    void pendingScarChoicePreventsAutomaticNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(false, true, true))
                .isFalse();
    }

    @Test
    void noScorableObjectiveAndNoScarChoiceStillAutoMarksNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(false, true, false))
                .isTrue();
    }

    @Test
    void homeSystemRestrictionStillAutoMarksNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(true, false, true))
                .isTrue();
    }

    @Test
    void immediatelyScorableObjectiveDoesNotAutoMarkNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(true, true, false))
                .isFalse();
    }

    // A 30-seat megagame sent these one message per player; the flood timed out the scoring buttons.
    @Test
    void notScoringPlayersAreListedInOneMessage() {
        assertThat(StatusHelper.notScoringSummary(List.of("Jade", "Navy"), List.of("Rust")))
                .isEqualTo(
                        "Marked as not scoring a public objective, as the bot believes they cannot score one: Jade, Navy.\n"
                                + "Opted not to score a secret objective at this point in time: Rust.");
    }

    @Test
    void onlyNonFogMegagamesBatchStatusNotices() {
        Game game = new Game();
        assertThat(StatusHelper.batchesStatusNotices(game)).isFalse();
        game.addTag(MegagameGroupService.SC_GROUPS_TAG);
        assertThat(StatusHelper.batchesStatusNotices(game)).isTrue();
        // Fog keeps one message per player: the notices go to private channels.
        game.setFowMode(true);
        assertThat(StatusHelper.batchesStatusNotices(game)).isFalse();
    }

    @Test
    void emptyListsLeaveNoSummary() {
        assertThat(StatusHelper.notScoringSummary(List.of(), List.of("Rust")))
                .isEqualTo("Opted not to score a secret objective at this point in time: Rust.");
        assertThat(StatusHelper.notScoringSummary(List.of(), List.of())).isEmpty();
    }
}
