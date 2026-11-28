package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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

    // Every chance names its seat and objective kind; held secrets get their own chances, and each seat's tokens are
    // read as the status phase begins.
    @Test
    void recordsSecretChancesAndTokensPerSeat() {
        test.sol.setSecret("mtm");
        test.sol.setSecret("lsc");
        test.sol.setTacticalCC(1);
        test.sol.setFleetCC(4);
        test.sol.setStrategicCC(2);
        everyonePassed();
        test.game.setPhaseOfGame("statusScoring");
        tracker.observe(test.game);
        test.sol.setSecretScored("lsc");
        test.game.setPhaseOfGame("agenda");
        tracker.observe(test.game);

        ObjectiveTracker.Summary summary = tracker.summary(test.game);
        assertThat(summary.chances())
                .extracting(ObjectiveTracker.Chance::faction)
                .containsExactlyInAnyOrder("sol", "nekro");
        assertThat(summary.chances())
                .allSatisfy(chance -> assertThat(chance.kind()).isEqualTo(ObjectiveTracker.STAGE_ONE));
        assertThat(summary.secretChances()).hasSize(2).allSatisfy(chance -> {
            assertThat(chance.faction()).isEqualTo("sol");
            assertThat(chance.kind()).isEqualTo(ObjectiveTracker.SECRET);
            assertThat(chance.round()).isEqualTo(1);
        });
        assertThat(summary.secretChances())
                .filteredOn(chance -> "mtm".equals(chance.objective()))
                .singleElement()
                .satisfies(chance -> {
                    assertThat(chance.scored()).isFalse();
                    assertThat(chance.shortBy()).isEqualTo(4);
                });
        assertThat(summary.secretChances())
                .filteredOn(chance -> "lsc".equals(chance.objective()))
                .singleElement()
                .satisfies(chance -> assertThat(chance.scored()).isTrue());
        assertThat(summary.tokens())
                .filteredOn(count -> "sol".equals(count.faction()))
                .containsExactly(new ObjectiveTracker.TokenCount("sol", 1, 1, 4, 2));
    }

    @Test
    void seatLinesListWhatEachSeatMissedAndItsTokens() {
        List<ObjectiveTracker.Chance> chances = List.of(
                new ObjectiveTracker.Chance("xxcha", "expand_borders", ObjectiveTracker.STAGE_ONE, 2, 1, false),
                new ObjectiveTracker.Chance("xxcha", "sway_council", ObjectiveTracker.STAGE_ONE, 2, 0, false),
                new ObjectiveTracker.Chance("xxcha", "corner", ObjectiveTracker.STAGE_ONE, 2, 0, true),
                new ObjectiveTracker.Chance(
                        "xxcha", "mtm", ObjectiveTracker.SECRET, 2, ObjectiveTracker.UNMODELLED, false),
                new ObjectiveTracker.Chance("xxcha", "conquer", ObjectiveTracker.STAGE_TWO, 3, 1, true));
        List<ObjectiveTracker.TokenCount> tokens = List.of(
                new ObjectiveTracker.TokenCount("xxcha", 3, 0, 3, 1),
                new ObjectiveTracker.TokenCount("xxcha", 2, 2, 3, 2));

        assertThat(ObjectiveTracker.seatLines("g1", chances, tokens))
                .containsExactly(
                        "SEATOBJ g1 xxcha: r2 publics missed [expand_borders 1, sway_council met] scored 1 secrets"
                                + " missed [mtm ?]; r3 publics missed [] scored 1 secrets missed []",
                        "SEATTOKENS g1 xxcha (tactic/fleet/strategy): r2 2/3/2, r3 0/3/1");
    }

    // Over the batch: scored / missed / missed though met / missed by 1 per objective kind, and token averages.
    @Test
    void factionLinesTallyChancesAndAverageTokens() {
        ObjectiveTracker.Summary first = summaryWith(
                List.of(
                        new ObjectiveTracker.Chance("xxcha", "a", ObjectiveTracker.STAGE_ONE, 1, 0, true),
                        new ObjectiveTracker.Chance("xxcha", "b", ObjectiveTracker.STAGE_ONE, 1, 0, false),
                        new ObjectiveTracker.Chance("xxcha", "c", ObjectiveTracker.STAGE_ONE, 1, 1, false),
                        new ObjectiveTracker.Chance("sol", "c", ObjectiveTracker.STAGE_TWO, 1, 3, false)),
                List.of(new ObjectiveTracker.Chance("xxcha", "mtm", ObjectiveTracker.SECRET, 1, 1, false)),
                List.of(new ObjectiveTracker.TokenCount("xxcha", 1, 2, 3, 1)));
        ObjectiveTracker.Summary second = summaryWith(
                List.of(new ObjectiveTracker.Chance("xxcha", "d", ObjectiveTracker.STAGE_TWO, 3, 1, false)),
                List.of(new ObjectiveTracker.Chance("xxcha", "lsc", ObjectiveTracker.SECRET, 2, 0, true)),
                List.of(new ObjectiveTracker.TokenCount("xxcha", 2, 0, 4, 2)));

        assertThat(ObjectiveTracker.factionLines(List.of(first, second)))
                .containsExactly(
                        "Objective chances per faction at status phases (scored / missed / missed though met / missed"
                                + " by 1):",
                        "  sol: stage I 0/0/0/0, stage II 0/1/0/0, secret 0/0/0/0",
                        "  xxcha: stage I 1/2/1/1, stage II 0/1/0/1, secret 1/1/0/1",
                        "Average tokens per faction as a status phase began (tactic/fleet/strategy):",
                        "  xxcha: 1.0/3.5/1.5 over 2 seat status phases");
    }

    private static ObjectiveTracker.Summary summaryWith(
            List<ObjectiveTracker.Chance> chances,
            List<ObjectiveTracker.Chance> secretChances,
            List<ObjectiveTracker.TokenCount> tokens) {
        ObjectiveTracker.Summary none = ObjectiveTracker.Summary.NOTHING;
        return new ObjectiveTracker.Summary(
                List.of(),
                List.of(),
                chances,
                none.leaderPath(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                2,
                secretChances,
                tokens);
    }
}
