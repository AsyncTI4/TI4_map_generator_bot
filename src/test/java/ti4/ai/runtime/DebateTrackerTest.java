package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class DebateTrackerTest extends BaseTi4Test {

    // Sol holds Drive the Debate, casts 4 votes for itself against Nekro's 2 and is elected, then the next agenda is
    // revealed before it scored: the election is reported as elected but not scored.
    @Test
    void reportsAnElectedHolderThatNeverScored() {
        AiTestGame test = new AiTestGame();
        DebateTracker tracker = new DebateTracker();
        test.sol.setSecret("dtd");
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_standardization");
        test.game.setCurrentAgendaVote("sol", "blue_4");
        test.game.setCurrentAgendaVote("nekro", "black_2");
        tracker.observe(test.game);

        test.game.setPhaseOfGame("agendaEnd");
        test.game.setStoredValue("resolvedAgendaOutcome", "sol");
        tracker.observe(test.game);

        test.game.setPhaseOfGame("agendawaiting");
        test.game.setStoredValue("resolvedAgendaOutcome", "");
        test.game.setCurrentAgendaInfo("Law_Elect Player_8_minister_war");
        tracker.observe(test.game);
        List<String> lines = tracker.lines(test.game);

        assertThat(lines.getFirst()).contains("holder sol cast 4", "winner sol got 4", "elected true", "scored false");
        assertThat(lines.getLast()).contains("holder elected 1", "scored 0", "elected not scored 1");
    }

    @Test
    void ignoresAgendasThatElectNobody() {
        AiTestGame test = new AiTestGame();
        DebateTracker tracker = new DebateTracker();
        test.sol.setSecret("dtd");
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");
        tracker.observe(test.game);

        assertThat(tracker.lines(test.game).getLast()).contains("0 elections");
    }
}
