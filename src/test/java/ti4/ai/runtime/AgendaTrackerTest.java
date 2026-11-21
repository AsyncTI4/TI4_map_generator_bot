package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.AgendaUtilityProbe;
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

    // Galactic Threat pays Nekro a technology from a winning voter (AgendaHelper.getWinningVoters). Nobody votes on
    // Arms Reduction and the speaker picks For, the outcome Nekro predicted: the rider is not won and nets nothing.
    @Test
    void aGalacticThreatOnAnOutcomeNobodyVotedForIsNotWon() {
        AiTestGame test = new AiTestGame();
        AgendaTracker tracker = new AgendaTracker();
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Directive_For/Against_8_arms_reduction");
        tracker.observe(test.game, 0);

        test.game.setCurrentAgendaVote("for", "nekro_Galactic Threat Rider");
        tracker.observe(test.game, 60_000);
        test.game.setStoredValue("resolvedAgendaOutcome", "for");
        tracker.observe(test.game, 120_000);

        AgendaTracker.Rider rider = tracker.summary(test.game, 120_000)
                .agendas()
                .getFirst()
                .riders()
                .getFirst();
        assertThat(rider.won()).isFalse();
        assertThat(rider.net()).isZero();
    }

    // With a voter on the predicted outcome the rider is won, and it is worth the technology Nekro actually copied.
    @Test
    void aGalacticThreatIsWorthTheTechnologyNekroCopied() {
        AiTestGame test = new AiTestGame();
        AgendaTracker tracker = new AgendaTracker();
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Directive_For/Against_8_arms_reduction");
        tracker.observe(test.game, 0);

        test.game.setCurrentAgendaVote("for", "nekro_Galactic Threat Rider;sol_3");
        tracker.observe(test.game, 60_000);
        test.game.setStoredValue("resolvedAgendaOutcome", "for");
        String copied = test.sol.getTechs().stream()
                .filter(tech -> !test.nekro.hasTech(tech))
                .findFirst()
                .orElseThrow();
        test.nekro.addTech(copied);
        tracker.observe(test.game, 120_000);

        AgendaTracker.Rider rider = tracker.summary(test.game, 120_000)
                .agendas()
                .getFirst()
                .riders()
                .getFirst();
        assertThat(rider.won()).isTrue();
        assertThat(rider.payoff()).isEqualTo(ti4.ai.strategy.CopiedTechPolicy.copiedWorth(copied));
        assertThat(rider.net()).isPositive();
    }

    // An A/B on the value table must grade treatment and control on one yardstick: switching AGENDA_VALUE_TABLE off
    // for a seat changes how it votes, never the utilities the tracker records for it.
    @Test
    void utilitiesUseOneReferenceTableWhateverTheSeatsFlags() {
        AiTestGame test = new AiTestGame();
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_8_minister_sciences");
        Map<String, Double> enabled = AgendaUtilityProbe.utilities(test.game, test.sol);

        AiExperiments.disable(test.sol, Set.of(Experiment.AGENDA_VALUE_TABLE));
        assertThat(AiExperiments.enabled(test.sol, Experiment.AGENDA_VALUE_TABLE))
                .isFalse();
        assertThat(AgendaUtilityProbe.utilities(test.game, test.sol)).isEqualTo(enabled);

        AgendaTracker tracker = new AgendaTracker();
        tracker.observe(test.game, 0);
        test.game.setStoredValue("resolvedAgendaOutcome", "sol");
        tracker.observe(test.game, 60_000);
        test.game.setPhaseOfGame("strategy");
        AgendaTracker.Agenda agenda =
                tracker.summary(test.game, 60_000).agendas().getFirst();
        // Nekro keeps the table on, Sol has it off; both are valued on the expert table (Sciences is 4 TG in round 2).
        double mean = enabled.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        assertThat(agenda.seats().get("sol").realizedUtility()).isEqualTo(enabled.get("sol") - mean);
    }

    // Covert Legislation: the hidden agenda the speaker drew (top of the deck, sent to the speaker) is what gets
    // valued and reported, not the flat "covert" prior.
    @Test
    void valuesTheAgendaHiddenUnderCovertLegislation() {
        AiTestGame test = new AiTestGame();
        test.game.setAgendas(new ArrayList<>(List.of("minister_war", "arms_reduction")));
        test.game.drawAgenda();
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_CL_covert");
        assertThat(AgendaUtilityProbe.currentAgenda(test.game)).isEqualTo("covert");
        assertThat(AgendaTracker.valuedAgenda(test.game, test.game.getCurrentAgendaInfo()))
                .isEqualTo("minister_war");

        AgendaTracker tracker = new AgendaTracker();
        tracker.observe(test.game, 0);
        test.game.setStoredValue("resolvedAgendaOutcome", "sol");
        tracker.observe(test.game, 60_000);
        test.game.setPhaseOfGame("strategy");
        AgendaTracker.Agenda agenda =
                tracker.summary(test.game, 60_000).agendas().getFirst();

        assertThat(agenda.agenda()).isEqualTo("minister_war");
        Map<String, Double> war = AgendaUtilityProbe.utilities(test.game, test.sol, "minister_war");
        double mean =
                war.values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        assertThat(agenda.seats().get("sol").realizedUtility()).isEqualTo(war.get("sol") - mean);
    }

    // An agenda the probe cannot value is its own defect, not a pile of seats without a matching outcome.
    @Test
    void anAgendaWithoutUtilitiesIsCountedAsMissingNotUnmatched() {
        AiTestGame test = new AiTestGame();
        AgendaTracker tracker = new AgendaTracker();
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_8_notanagenda");
        tracker.observe(test.game, 0);
        test.game.setStoredValue("resolvedAgendaOutcome", "sol");
        tracker.observe(test.game, 60_000);
        test.game.setPhaseOfGame("strategy");

        AgendaTracker.Summary summary = tracker.summary(test.game, 60_000);
        assertThat(summary.agendas().getFirst().utilitiesMissing()).isTrue();
        assertThat(summary.missingUtilities()).isEqualTo(1);
        assertThat(summary.unmatchedUtilities()).isZero();
    }

    // Tie-break presses are attributed from the call stack: the referee and a stalled seat's fallback are defects,
    // the speaker AI's own press is not, and each defect lands on the agenda that was open.
    @Test
    void countsTieBreaksByWhoPressedThem() {
        assertThat(SelfPlayEnvironment.tieBreakerOf(stack("referee"))).contains(AgendaTracker.TieBreaker.REFEREE);
        assertThat(SelfPlayEnvironment.tieBreakerOf(stack("fallBack"))).contains(AgendaTracker.TieBreaker.FALLBACK);
        assertThat(SelfPlayEnvironment.tieBreakerOf(stack("pressDelegated")))
                .contains(AgendaTracker.TieBreaker.FALLBACK);
        assertThat(SelfPlayEnvironment.tieBreakerOf(stack("pressAsSeat"))).isEmpty();

        AiTestGame test = new AiTestGame();
        AgendaTracker tracker = new AgendaTracker();
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_8_minister_war");
        tracker.observe(test.game, 0);
        tracker.tieBroken(AgendaTracker.TieBreaker.REFEREE);
        tracker.tieBroken(AgendaTracker.TieBreaker.FALLBACK);
        test.game.setPhaseOfGame("strategy");

        AgendaTracker.Summary summary = tracker.summary(test.game, 60_000);
        assertThat(summary.refereeTieBreaks()).isEqualTo(1);
        assertThat(summary.fallbackTieBreaks()).isEqualTo(1);
        assertThat(summary.agendas().getFirst().refereeTieBreaks()).isEqualTo(1);
        assertThat(AgendaTracker.lines(summary).getLast()).contains("referee-broken ties 1", "fallback-broken ties 1");
    }

    private static StackTraceElement[] stack(String tickRunnerMethod) {
        return new StackTraceElement[] {
            new StackTraceElement("ti4.ai.actuation.AiActuator", "press", null, 1),
            new StackTraceElement("ti4.ai.runtime.AiTickRunner", "pressAsSeat", null, 2),
            new StackTraceElement("ti4.ai.runtime.AiTickRunner", tickRunnerMethod, null, 3)
        };
    }
}
