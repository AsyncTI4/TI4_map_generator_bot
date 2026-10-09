package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.testUtils.BaseTi4Test;

class AgendaPolicyTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    private void givePoints(String userId, int points) {
        test.game.scorePublicObjective(userId, test.game.addCustomPO("Test points", points));
    }

    private Optional<String> tieBreak(String agenda, String... customIds) {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_" + agenda);
        AiPrompt tie = prompt("tie", PromptSource.PUBLIC, NOW, customIds);
        return AgendaPolicy.chooseOutcome(test.game, test.nekro, tie.enabledButtons())
                .map(PromptButton::customId);
    }

    // With nobody voting, the speaker decides: an agenda that hands the elected player a victory point goes to
    // the speaker itself.
    @Test
    void electsItselfForAVictoryPointAgenda() {
        assertThat(tieBreak(
                        "shard_of_the_throne",
                        "resolveAgendaVote_outcomeTie*_sol",
                        "resolveAgendaVote_outcomeTie*_nekro"))
                .contains("resolveAgendaVote_outcomeTie*_nekro");
    }

    // A harmful election goes to someone else, preferring the leader.
    @Test
    void neverElectsItselfForPublicExecution() {
        givePoints(test.sol.getUserID(), 4);

        assertThat(tieBreak("execution", "resolveAgendaVote_outcomeTie*_nekro", "resolveAgendaVote_outcomeTie*_sol"))
                .contains("resolveAgendaVote_outcomeTie*_sol");
    }

    // Seed of an Empire gives the leader a point "for" and the trailing player a point "against".
    @Test
    void picksTheSeedOfAnEmpireSideThatScoresForItself() {
        givePoints(test.sol.getUserID(), 5);
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_seed_empire");
        AiPrompt tie = prompt(
                "tie",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_resolveAgendaVote_outcomeTie* for",
                "FFCC_nekro_resolveAgendaVote_outcomeTie* against");

        assertThat(AgendaPolicy.chooseOutcome(test.game, test.nekro, tie.enabledButtons())
                        .map(PromptButton::customId))
                .contains("FFCC_nekro_resolveAgendaVote_outcomeTie* against");
    }

    @Test
    void keepsTheFirstOutcomeForAnAgendaWithoutAPreference() {
        assertThat(tieBreak(
                        "arms_reduction", "resolveAgendaVote_outcomeTie*_for", "resolveAgendaVote_outcomeTie*_against"))
                .contains("resolveAgendaVote_outcomeTie*_for");
    }
}
