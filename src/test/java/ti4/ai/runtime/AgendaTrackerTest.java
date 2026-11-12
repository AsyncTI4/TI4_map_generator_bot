package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class AgendaTrackerTest extends BaseTi4Test {

    // Sol votes 4 for itself on Minister of War, Nekro 2 for itself and places a Galactic Threat on Sol (no card
    // spent).
    // Sol is elected and gains 2 TG before the next reveal closes the agenda.
    @Test
    void recordsVotesRidersOutcomeAndDeltasUntilTheNextReveal() {
        AiTestGame test = new AiTestGame();
        AgendaTracker tracker = new AgendaTracker();
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_8_minister_war");
        tracker.observe(test.game, 0);

        test.game.setCurrentAgendaVote("sol", "sol_4");
        test.game.setCurrentAgendaVote("nekro", "nekro_2");
        test.game.setCurrentAgendaVote("sol", "sol_4;nekro_Galactic Threat Rider");
        tracker.observe(test.game, 60_000);

        test.game.setStoredValue("resolvedAgendaOutcome", "sol");
        test.sol.setTg(test.sol.getTg() + 2);
        tracker.observe(test.game, 120_000);

        test.game.setStoredValue("resolvedAgendaOutcome", "");
        test.game.setCurrentAgendaInfo("Directive_For/Against_9_arms_reduction");
        tracker.observe(test.game, 180_000);
        test.game.setPhaseOfGame("strategy");
        tracker.observe(test.game, 240_000);

        AgendaTracker.Summary summary = tracker.summary(test.game, 240_000);
        assertThat(summary.agendas()).hasSize(2);
        AgendaTracker.Agenda first = summary.agendas().getFirst();
        assertThat(first.agenda()).isEqualTo("minister_war");
        assertThat(first.outcome()).isEqualTo("sol");
        assertThat(first.waitMillis()).isEqualTo(120_000);
        assertThat(first.votesBySeat()).containsEntry("sol", "sol 4").containsEntry("nekro", "nekro 2");
        assertThat(first.riders()).singleElement().satisfies(rider -> {
            assertThat(rider.faction()).isEqualTo("nekro");
            assertThat(rider.won()).isTrue();
            assertThat(rider.net()).isEqualTo(rider.payoff());
        });
        assertThat(first.seats().get("sol").tgDelta()).isEqualTo(2);
        assertThat(first.seats().get("sol").votes()).isEqualTo(4);
        // Sol valued its own election above the average outcome; Nekro valued it below.
        assertThat(first.seats().get("sol").realizedUtility()).isPositive();
        assertThat(first.seats().get("nekro").realizedUtility()).isNegative();
        assertThat(summary.agendas().get(1).resolved()).isFalse();
        assertThat(AgendaTracker.lines(summary).getLast()).contains("2 agendas (1 resolved)", "referee-broken ties 0");
    }
}
