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
                .contains(
                        "      - t+0.0m, voting: proposed to Xxcha: [xxcha promises sol not to vote against against, sol pays 4 TG")
                .contains("      - t+0.0m, voting: offered by Sol: [xxcha promises sol not to vote against against")
                .contains("    - result reveal to close: no change")
                .doesNotContain("BALLOTS")
                .doesNotContain("ATTRIBUTION");
        assertThat(block.indexOf("  - Sol: ")).isGreaterThan(block.indexOf("  - Xxcha: "));
        assertThat(block).contains("  - Nekro: ").contains("    - ballot: cannot vote (Galactic Threat)");
    }

    // User (2026-11-13): "Each deal in its own window. Veto/Quash bounties are 'when' deals; vote deals come later,
    // made
    // with each voter at its turn to vote." Every deal and every use is stamped with minutes since reveal, the window
    // it
    // happened in and, during voting, whose turn it was, so a trace shows whether each deal kept to its window.
    @Test
    void dealsAndUsesSayWhenAndInWhichWindowTheyHappened() {
        AiTestGame test = new AiTestGame();
        Player xxcha = test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setCurrentAgendaInfo(INFO);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setRound(1);
        test.game.setPhaseOfGame("agendawaiting");
        AgendaTrace.Window window = new AgendaTrace(() -> null).open(test.game, INFO, 0);
        DiscardBounties.save(
                test.game,
                new DiscardBounties.Bounty(
                        test.game.getRound(), AGENDA, "sol", 2, 0, 0, "", -1, DiscardBounties.Status.OPEN));
        window.observe(test.game, 30_000, false);
        assertThat(AgendaTrace.windowOf(test.game)).isEqualTo("when");

        test.game.setStoredValue("whensResolved", "Yes");
        assertThat(AgendaTrace.windowOf(test.game)).isEqualTo("after");

        test.isActive(xxcha, "agendaVoting");
        Promise sale = Promise.offer(
                test.game, Promise.Term.VOTE_FOR, xxcha, test.sol, xxcha, AGENDA, "for", 5, 1, 360_000, 420_000);
        PromiseBook.save(test.game, sale);
        window.observe(test.game, 372_000, false);
        PromiseBook.save(test.game, sale.accepted(test.game));
        window.observe(test.game, 390_000, false);

        AgendaTrace.RiderSeen rider =
                new AgendaTrace.RiderSeen("sol", "Trade Rider", "for", 3.0, 0.5, Double.NaN, 90_000, 0, "after");
        String block = window.close(
                test.game,
                "aiselfplaytest",
                agenda(Map.of("xxcha", "for 5"), List.of()),
                List.of(rider),
                Map.of("xxcha", values(1.0, 0.0), "sol", values(2.0, 0.0), "nekro", values(0.0, 0.0)));

        assertThat(block)
                .contains("      - t+0.5m, when: posted a discard bounty: 2 TG for a Veto - open")
                .contains("      - t+6.0m, voting, Xxcha's turn: offered by Xxcha: [xxcha promises sol its votes (5)"
                        + " for for, sol pays 1 TG; proposed by xxcha; ACCEPTED]")
                .contains("at t+6.5m, voting, Xxcha's turn")
                .contains("Trade Rider on For (valued 3.0 TG at 50%): ")
                .contains("(t+1.5m, after)");
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
