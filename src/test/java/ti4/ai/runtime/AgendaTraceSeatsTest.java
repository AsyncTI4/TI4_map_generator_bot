package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.DiscardBounties;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The AGENDA TRACE block lists every seat in voting order as a bulleted block: votes at reveal, ballot, odds, top
// likes and dislikes, what it used and the deals it made.
class AgendaTraceSeatsTest extends BaseTi4Test {

    private static final String INFO = "Directive_For/Against_8_arms_reduction";
    private static final String AGENDA = "arms_reduction";

    private static AgendaTracker.Agenda agenda(Map<String, String> votes, List<AgendaTracker.Rider> riders) {
        return new AgendaTracker.Agenda(
                1, 1, INFO, AGENDA, "sol", "for", votes, riders, Map.of(), 0, false, 0, 0, false);
    }

    private static Map<String, Double> values(double forValue, double againstValue) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("for", forValue);
        values.put("against", againstValue);
        return values;
    }

    @Test
    void eachSeatGetsItsOwnBulletedBlockInVotingOrder() {
        AiTestGame test = new AiTestGame();
        Player xxcha = test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo(INFO);
        test.game.setSpeakerUserID(test.sol.getUserID());
        AgendaTrace.Window window = new AgendaTrace(() -> null).open(test.game, INFO, 0);
        PromiseBook.save(
                test.game,
                Promise.offer(
                                test.game,
                                Promise.Term.NOT_AGAINST,
                                xxcha,
                                test.sol,
                                test.sol,
                                AGENDA,
                                "against",
                                0,
                                4,
                                0,
                                60_000)
                        .accepted(test.game));
        window.observe(test.game, 1, false);
        List<AgendaTracker.Rider> riders = List.of();

        String block = window.close(
                test.game,
                "aiselfplaytest",
                agenda(Map.of("xxcha", "for 5"), riders),
                List.of(),
                Map.of("xxcha", values(3.0, 0.0), "sol", values(-6.0, 0.0), "nekro", values(0.0, 0.0)));

        List<String> lines = List.of(block.split("\\R"));
        assertThat(lines.get(0)).startsWith("AGENDA TRACE aiselfplaytest r1 #1");
        assertThat(lines.get(1)).startsWith("  VOTING ORDER: ");
        assertThat(lines.get(2)).startsWith("  SEATS in voting order.");
        assertThat(block)
                .contains("  - Xxcha: 0 votes at reveal")
                .contains("    - ballot: cast 5 For")
                .contains("    - likes: For +1.5")
                .contains("    - dislikes: Against -1.5")
                .contains("  - Sol: ")
                .contains("    - dislikes: For -3.0")
                .contains("    - used: nothing")
                .contains("      - proposed to Xxcha: [xxcha promises sol not to vote against against, sol pays 4 TG")
                .contains("      - offered by Sol: [xxcha promises sol not to vote against against")
                .contains("    - result reveal to close: no change")
                .doesNotContain("BALLOTS")
                .doesNotContain("ATTRIBUTION");
        assertThat(block.indexOf("  - Sol: ")).isGreaterThan(block.indexOf("  - Xxcha: "));
        assertThat(block).contains("  - Nekro: ").contains("    - ballot: cannot vote (Galactic Threat)");
    }

    @Test
    void likesAndDislikesAreTopThreeAgainstTheMean() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("a", 4.0);
        values.put("b", 2.0);
        values.put("c", 1.0);
        values.put("d", 0.0);
        values.put("e", -2.0);

        assertThat(AgendaTrace.ranked(values, true)).isEqualTo("A +3.0, B +1.0");
        assertThat(AgendaTrace.ranked(values, false)).isEqualTo("E -3.0, D -1.0");
        assertThat(AgendaTrace.ranked(values(1.0, 1.0), true)).isEqualTo("none");
    }

    @Test
    void aBountyReadsAsWhatItPaysForWhat() {
        DiscardBounties.Bounty bounty =
                new DiscardBounties.Bounty(1, AGENDA, "hacan", 3, 5, 0, "xxcha", 2, DiscardBounties.Status.LAPSED);

        assertThat(AgendaTrace.bountyText(bounty))
                .isEqualTo("posted a discard bounty: 3 TG for a Veto, 5 TG to Xxcha for a Quash - lapsed");
    }
}
