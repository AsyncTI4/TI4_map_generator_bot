package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgendaTraceVotingOrderTest extends BaseTi4Test {

    private static final String INFO = "Directive_For/Against_8_arms_reduction";

    private static AgendaTracker.Agenda agenda(
            String speaker, String outcome, Map<String, String> votes, List<AgendaTracker.Rider> riders) {
        return new AgendaTracker.Agenda(
                1, 1, INFO, "arms_reduction", speaker, outcome, votes, riders, Map.of(), 0, false, 0, 0, false);
    }

    // Xxcha and Sol tie 5-5 and the speaker breaks it; Nekro cannot vote under Galactic Threat. The line lists every
    // seat in the engine's voting order (the speaker last) with its votes at reveal, then the tally and the tie-break.
    @Test
    void listsEverySeatInVotingOrderWithTheTallyAndTheTieBreak() {
        AiTestGame test = new AiTestGame();
        Player xxcha = test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo(INFO);
        test.game.setSpeakerUserID(test.sol.getUserID());
        AgendaTrace.Window window = new AgendaTrace(() -> null).open(test.game, INFO, 0);

        String line = window.votingOrderLine(
                test.game,
                agenda("sol", "against", Map.of("xxcha", "for 5", "sol", "against 5"), List.of()),
                List.of(new AgendaTrace.Event(1, "sol", AgendaTrace.Kind.OTHER, "break the agenda tie as speaker")));

        assertThat(line)
                .startsWith("VOTING ORDER: ")
                .contains("Nekro (0) -", "Xxcha (", ") 5 For", "Sol (", ") 5 Against")
                .contains("; tally ")
                .endsWith("; speaker Sol broke the 5-5 tie: Against");
        assertThat(line.indexOf("Sol (")).isGreaterThan(line.indexOf("Xxcha ("));
        assertThat(xxcha.getFaction()).isEqualTo("xxcha");
    }

    // The engine never asks a seat that cannot vote: one that placed a rider, or one with no votes at reveal.
    @Test
    void namesSeatsTheEngineSkipped() {
        AiTestGame test = new AiTestGame();
        test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo(INFO);
        test.game.setSpeakerUserID(test.sol.getUserID());
        AgendaTrace.Window window = new AgendaTrace(() -> null).open(test.game, INFO, 0);
        List<AgendaTracker.Rider> riders =
                List.of(new AgendaTracker.Rider("xxcha", "Leadership Rider", "for", 4.5, true, 3));

        String line = window.votingOrderLine(test.game, agenda("sol", "for", Map.of(), riders), List.of());

        assertThat(line).contains("Xxcha (0) skipped (rider)", "Sol (0) skipped (no votes)", "no votes cast");
        assertThat(line).endsWith("; the speaker broke the no-vote tie: For");
    }
}
