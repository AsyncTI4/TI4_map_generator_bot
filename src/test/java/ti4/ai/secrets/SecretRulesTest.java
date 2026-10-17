package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class SecretRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        for (String law : new String[] {"checks", "regulations", "sanctions"}) test.game.addLaw(law, null);
        test.nekro.setSecret("dp");
    }

    // Dictate Policy is an agenda-phase secret: with three laws in play it is scored during the agenda phase, never
    // with the one status-phase secret.
    @Test
    void neverScoresAnAgendaSecretInTheStatusPhase() {
        test.game.setPhaseOfGame("statusScoring");
        AiPrompt status = prompt("status", PromptSource.PUBLIC, NOW, "so_no_scoring", "get_so_score_buttons");

        assertThat(SecretRules.scoreStatusSecret(test.context(status)).map(AiTestGame::pressedId))
                .contains("so_no_scoring");
    }

    @Test
    void scoresDictatePolicyInTheAgendaPhase() {
        test.game.setPhaseOfGame("agendawaiting");
        int hand = test.nekro.getSecretsUnscored().get("dp");
        AiPrompt score = prompt("score", PromptSource.AI_THREAD, NOW, "so_score_hand_" + hand);

        assertThat(SecretRules.scoreAgendaSecret(test.context(score)).map(AiTestGame::pressedId))
                .contains("so_score_hand_" + hand);
    }

    // Drive the Debate counts only an election in this agenda phase. The bot keeps the last outcome until the next
    // agenda is flipped, so last round's election must not score while the new phase waits for its first flip.
    @Test
    void scoresDriveTheDebateOnlyForThisPhasesElection() {
        test.nekro.setSecret("dtd");
        int hand = test.nekro.getSecretsUnscored().get("dtd");
        AiPrompt score = prompt("score", PromptSource.AI_THREAD, NOW, "so_score_hand_" + hand);
        test.game.setStoredValue("resolvedAgendaOutcome", test.nekro.getFaction());

        test.game.setPhaseOfGame("agenda");
        assertThat(SecretRules.scoreAgendaSecret(test.context(score)).map(AiTestGame::pressedId))
                .isNotEqualTo(java.util.Optional.of("so_score_hand_" + hand));

        test.game.setPhaseOfGame("agendaEnd");
        assertThat(SecretRules.scoreAgendaSecret(test.context(score)).map(AiTestGame::pressedId))
                .contains("so_score_hand_" + hand);
    }

    // Without the scoring buttons in view, it asks for them rather than giving up on the secret.
    @Test
    void asksForTheScoringButtonsWhenNoneAreVisible() {
        test.game.setPhaseOfGame("agendawaiting");
        AiPrompt info = prompt("info", PromptSource.AI_THREAD, NOW, "get_so_score_buttons", "get_so_discard_buttons");

        assertThat(SecretRules.scoreAgendaSecret(test.context(info)).map(AiTestGame::pressedId))
                .contains("get_so_score_buttons");
    }
}
