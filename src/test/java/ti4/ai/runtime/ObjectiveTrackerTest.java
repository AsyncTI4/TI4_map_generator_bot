package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class ObjectiveTrackerTest extends BaseTi4Test {

    private static final String EXPAND_BORDERS = "expand_borders";

    private AiTestGame test;
    private ObjectiveTracker tracker;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.game.getRevealedPublicObjectives().put(EXPAND_BORDERS, 1);
        tracker = new ObjectiveTracker();
    }

    private void everyonePassed() {
        test.game.setPhaseOfGame("action");
        test.game.getRealPlayers().forEach(seat -> seat.setPassed(true));
        tracker.observe(test.game);
    }

    private void solScoresExpandBorders() {
        test.game.scorePublicObjective(
                test.sol.getUserID(), test.game.getRevealedPublicObjectives().get(EXPAND_BORDERS));
    }

    // A score queued during the action phase is applied as the status phase starts, so it has to count for that
    // status phase even though it is already there the first time the tracker sees the phase.
    @Test
    void countsAScoreAppliedAsTheStatusPhaseStarts() {
        everyonePassed();
        solScoresExpandBorders();
        test.game.setPhaseOfGame("statusScoring");
        tracker.observe(test.game);
        test.game.setPhaseOfGame("agenda");
        tracker.observe(test.game);

        ObjectiveTracker.Summary summary = tracker.summary(test.game);
        assertThat(summary.statusScoring())
                .extracting(ObjectiveTracker.StatusScoring::faction, ObjectiveTracker.StatusScoring::publics)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(test.sol.getFaction(), 1),
                        org.assertj.core.groups.Tuple.tuple(test.nekro.getFaction(), 0));
        assertThat(summary.scoringShareByRound(true)).containsExactly(0.5);
        assertThat(summary.reveals())
                .singleElement()
                .satisfies(reveal -> assertThat(reveal.scorers()).isEqualTo(1));
        assertThat(summary.leaderPath().stage1s()).isEqualTo(1);
    }

    // Nekro starts with no planets outside its home system, so it is all six short of Expand Borders.
    @Test
    void recordsHowFarShortAnUnscoredSeatWas() {
        everyonePassed();
        test.game.setPhaseOfGame("statusScoring");
        tracker.observe(test.game);

        ObjectiveTracker.Summary summary = tracker.summary(test.game);
        assertThat(summary.chances()).hasSize(2).allSatisfy(chance -> {
            assertThat(chance.objective()).isEqualTo(EXPAND_BORDERS);
            assertThat(chance.round()).isEqualTo(1);
            assertThat(chance.scored()).isFalse();
        });
        assertThat(summary.chances())
                .anySatisfy(chance -> assertThat(chance.shortBy()).isEqualTo(6));
    }

    // A status phase is counted once even though the phase moves from scoring to homework.
    @Test
    void takesOneSnapshotPerStatusPhase() {
        everyonePassed();
        test.game.setPhaseOfGame("statusScoring");
        tracker.observe(test.game);
        test.game.setPhaseOfGame("statusHomework");
        tracker.observe(test.game);
        test.game.setPhaseOfGame("agenda");
        tracker.observe(test.game);
        test.game.setPhaseOfGame("statusScoring");
        tracker.observe(test.game);

        assertThat(tracker.summary(test.game).statusScoring()).hasSize(2);
    }
}
