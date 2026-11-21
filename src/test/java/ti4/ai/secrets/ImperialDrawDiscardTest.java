package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.StandardBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class ImperialDrawDiscardTest extends BaseTi4Test {

    private AiTestGame test;

    // Round 5 of self-play seed 9701: the seat played Imperial, scored a public, drew a secret and now holds one secret
    // over its limit. The cards-info thread offers the discard opener; the actions channel offers the end-turn prompt.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(5);
        test.isActive(test.nekro, "action");
        test.nekro.addSC(8);
        test.game.setSCPlayed(8, true);
        test.nekro.setSecretScored("mrm");
        test.nekro.setSecretScored("uf");
        test.nekro.setSecret("fwp");
        test.nekro.setSecret("dtd");
    }

    @Test
    void discardsBackToTheLimitBeforeEndingTheTurn() {
        AiPrompt endTurn = prompt(
                "end",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_endOfTurnAbilities",
                "FFCC_nekro_doAnotherAction",
                "FFCC_nekro_endTurnWhenAllReactedTo_8");
        AiPrompt info = prompt("info", PromptSource.AI_THREAD, NOW, "get_so_score_buttons", "get_so_discard_buttons");

        assertThat(test.nekro.getSecretsUnscored()).hasSize(2);
        assertThat(AiTestGame.pressedId(new StandardBrain().decide(test.context(endTurn, info))))
                .isEqualTo("get_so_discard_buttons");
    }

    // The holder's own Imperial draw is queued behind a seat at its limit: nothing to discard yet, so it ends the turn.
    @Test
    void endsTheTurnWhileItsImperialDrawIsQueued() {
        test.nekro.removeSecret(test.nekro.getSecretsUnscored().get("dtd"));
        test.game.setStoredValue("queueToDrawSOs", "nekro*");
        test.game.setStoredValue("potentialBlockers", "sol*");
        AiPrompt endTurn = prompt(
                "end",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_endOfTurnAbilities",
                "FFCC_nekro_doAnotherAction",
                "FFCC_nekro_endTurnWhenAllReactedTo_8");
        AiPrompt card =
                prompt("imperial", PromptSource.PUBLIC, NOW - 60_000, "sc_follow_8", "sc_no_follow_8", "sc_draw_so");

        assertThat(new StandardBrain().decide(test.context(endTurn, card)))
                .isNotInstanceOf(ti4.ai.brain.AiDecision.Idle.class);
    }

    // Self-play seed 9701 round 5: another seat's played card still shows its follow buttons, the seat already pressed
    // them all but the engine has not marked it as followed (its draw is queued). Nothing is left to do for the follow,
    // so the seat must take its turn instead of idling on it until the fallback.
    @Test
    void takesItsTurnWhenAnAnsweredFollowIsStillOpen() {
        test.nekro.removeSC(8);
        test.game.setSCPlayed(8, false);
        test.sol.addSC(8);
        test.game.setSCPlayed(8, true);
        AiPrompt turn = prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_componentAction",
                "FFCC_nekro_passingAbilities");
        AiPrompt card =
                prompt("imperial", PromptSource.PUBLIC, NOW - 60_000, "sc_follow_8", "sc_no_follow_8", "sc_draw_so");
        java.util.Set<String> pressed =
                java.util.Set.of("imperial|sc_follow_8", "imperial|sc_no_follow_8", "imperial|sc_draw_so");

        assertThat(new StandardBrain().decide(test.context(pressed, turn, card)))
                .isNotInstanceOf(ti4.ai.brain.AiDecision.Idle.class);
    }
}
