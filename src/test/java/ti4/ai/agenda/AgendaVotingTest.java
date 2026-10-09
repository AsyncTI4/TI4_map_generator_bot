package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgendaVotingTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.place("01", "304");
        sol.addPlanet("jord");
        test.isActive(sol, "agendaVoting");
    }

    private String press(AiPrompt... prompts) {
        return AgendaVoting.next(test.contextFor(sol, java.util.Set.of(), NOW, prompts))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    // An agenda that gives the elected player a victory point: it votes, elects itself, spends its planets, then
    // confirms.
    @Test
    void electsItselfForShardOfTheThroneWithAllItsVotes() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        assertThat(press(start)).isEqualTo("FFCC_sol_vote");

        AiPrompt outcomes = prompt("outcomes", PromptSource.PUBLIC, NOW, "outcome_nekro", "outcome_sol");
        assertThat(press(outcomes)).isEqualTo("outcome_sol");

        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of("exhaustForVotes_planet_jord", "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (4)", "Done exhausting planets."));
        assertThat(press(planets)).isEqualTo("exhaustForVotes_planet_jord");

        // The bot records a planet spent for votes as "planet_<name>".
        sol.addSpentThing("planet_jord");
        assertThat(press(planets)).isEqualTo("FFCC_sol_proceedToFinalizingVote");

        AiPrompt confirm = prompt("confirm", PromptSource.PUBLIC, NOW, "FFCC_sol_resolveAgendaVote_4");
        assertThat(press(confirm)).isEqualTo("FFCC_sol_resolveAgendaVote_4");
    }

    // Galactic Threat: Nekro cannot vote, so it never plans a ballot, even on an agenda it would like to win and
    // with planets that have influence.
    @Test
    void nekroNeverPlansABallot() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.place("19", AiTestGame.neighbourOf("304"));
        test.nekro.addPlanet("wellon");

        assertThat(AgendaVoting.plan(test.game, test.nekro)).isEmpty();
        assertThat(AgendaVoting.plan(test.game, sol)).isPresent();
    }

    // Planet agendas restricted to a trait only take a planet with that trait, even though the card's target just
    // says "Elect Planet": a Research Team: Biotic goes on industrial Wellon, never on Jord, and Holy Planet of Ixth
    // (cultural) has no eligible planet of Sol's to vote for.
    @Test
    void votesOnlyForPlanetsWithTheRequiredTrait() {
        test.place("19", AiTestGame.neighbourOf("304"));
        sol.addPlanet("wellon");

        test.game.setCurrentAgendaInfo("Law_Elect Planet_7_rt_biotic");
        assertThat(AgendaVoting.plan(test.game, sol).map(AgendaVoting.Ballot::outcome))
                .contains("wellon");

        test.game.setCurrentAgendaInfo("Law_Elect Planet_8_holy_planet_of_ixth");
        assertThat(AgendaVoting.plan(test.game, sol)).isEmpty();
    }

    // The speaker's tie-break buttons share the confirm prefix; a ballot left in memory must not press them.
    @Test
    void neverConfirmsVotesOnATieBreakButton() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");
        press(start);

        AiPrompt tie = prompt("tie", PromptSource.PUBLIC, NOW, "FFCC_sol_resolveAgendaVote_outcomeTie*sol");
        assertThat(press(tie)).isEmpty();
    }

    // With no preference it leaves the vote to the abstain rule.
    @Test
    void abstainsWhenNoOutcomeMatters() {
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_sol_vote", "FFCC_sol_resolveAgendaVote_0");

        assertThat(press(start)).isEmpty();
    }

    // After a rider the bot can show the outcomes straight away; with nothing to gain the AI picks any outcome and
    // casts no votes, which the bot records as an abstention.
    @Test
    void castsNoVotesWhenTheOutcomesAreOfferedDirectly() {
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");
        AiPrompt outcomes =
                prompt("outcomes", PromptSource.PUBLIC, NOW, "FFCC_sol_outcome_for", "FFCC_sol_outcome_against");
        assertThat(press(outcomes)).isEqualTo("FFCC_sol_outcome_for");

        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                List.of("exhaustForVotes_planet_jord", "FFCC_sol_proceedToFinalizingVote"),
                List.of("Jord (4)", "Done exhausting planets."));
        assertThat(press(planets)).isEqualTo("FFCC_sol_proceedToFinalizingVote");
    }
}
