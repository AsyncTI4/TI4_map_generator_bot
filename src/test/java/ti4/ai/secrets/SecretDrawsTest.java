package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class SecretDrawsTest extends BaseTi4Test {

    // Mine Rare Minerals wants 4 hazardous planets, which the seat does not have: the rarest secret human players
    // finish.
    private static final String HOPELESS = "mrm";
    private static final String DICTATE_POLICY = "dp";
    private static final String MECATOL_ABILITY = "FFCC_nekro_planetAbilityExhaust_mrte";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        test.game.setVp(10);
        test.game.setRound(3);
    }

    private void scoreTwoSecrets() {
        test.nekro.setSecretScored("dhw");
        test.nekro.setSecretScored("gamf");
    }

    private void threeLawsInPlay() {
        test.game.addLaw("conventions", null);
        test.game.addLaw("minister_war", "sol");
        test.game.addLaw("shard_of_the_throne", "sol");
    }

    @Test
    void drawsWhileThereIsRoomForAnotherSecret() {
        assertThat(SecretDraws.worthDrawing(test.game, test.nekro)).isTrue();
    }

    @Test
    void neverDrawsOnceEverySecretIsScored() {
        test.nekro.setSecretScored("dhw");
        test.nekro.setSecretScored("gamf");
        test.nekro.setSecretScored("fsn");

        assertThat(SecretDraws.worthDrawing(test.game, test.nekro)).isFalse();
        assertThat(SecretDraws.worthRedrawing(test.game, test.nekro)).isFalse();
    }

    // Humans follow Imperial with a full hand to draw a better secret and discard the worst.
    @Test
    void drawsWithAFullHandWhenItsWeakestSecretIsHopeless() {
        scoreTwoSecrets();
        test.nekro.setSecret(HOPELESS);

        assertThat(SecretDrawValue.hasRoom(test.nekro)).isFalse();
        assertThat(SecretDraws.worthDrawing(test.game, test.nekro)).isTrue();
        assertThat(SecretDrawValue.of(test.game, test.nekro)).isGreaterThan(SecretDrawValue.SWAP_VALUE);
    }

    // A secret the seat can already score is kept: there is nothing to gain from a draw.
    @Test
    void keepsAFullHandItCanAlreadyScore() {
        scoreTwoSecrets();
        threeLawsInPlay();
        test.nekro.setSecret(DICTATE_POLICY);

        assertThat(SecretDraws.worthDrawing(test.game, test.nekro)).isFalse();
        assertThat(SecretDrawValue.of(test.game, test.nekro)).isLessThanOrEqualTo(SecretDrawValue.SWAP_VALUE);
    }

    // At the end of its turn, with Mecatol Rex's ability ready and a hopeless secret, the seat trades that secret in,
    // then discards it from the buttons the ability posts in its private thread.
    @Test
    void tradesAHopelessSecretAtMecatolRexAtTheEndOfItsTurn() {
        test.nekro.setSecret(HOPELESS);
        String discard = "discardSecret_" + test.nekro.getSecretsUnscored().get(HOPELESS) + "redraw";
        AiPrompt abilities = prompt("abilities", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", MECATOL_ABILITY);
        AiPrompt discards = prompt("discards", PromptSource.AI_THREAD, NOW, discard);
        AiTurnContext context = test.context(abilities, discards);

        assertThat(pressedId(
                        SecretRedrawRules.endOfTurn(context, List.of(abilities)).orElseThrow()))
                .isEqualTo(MECATOL_ABILITY);
        assertThat(SecretRedrawRules.endOfTurn(context, List.of(abilities))).isEmpty();
        assertThat(pressedId(SecretRedrawRules.discardForRedraw(context).orElseThrow()))
                .isEqualTo(discard);
        assertThat(SecretRedrawRules.discardForRedraw(context)).isEmpty();
    }

    @Test
    void leavesMecatolRexAloneWhenItsSecretIsAlreadyScorable() {
        threeLawsInPlay();
        test.nekro.setSecret(DICTATE_POLICY);
        AiPrompt abilities = prompt("abilities", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", MECATOL_ABILITY);

        assertThat(SecretRedrawRules.endOfTurn(test.context(abilities), List.of(abilities)))
                .isEmpty();
    }

    // The discard buttons only answer a redraw the seat started.
    @Test
    void ignoresRedrawButtonsItDidNotAskFor() {
        test.nekro.setSecret(HOPELESS);
        String discard = "discardSecret_" + test.nekro.getSecretsUnscored().get(HOPELESS) + "redraw";
        AiPrompt discards = prompt("discards", PromptSource.AI_THREAD, NOW, discard);

        assertThat(SecretRedrawRules.discardForRedraw(test.context(discards))).isEmpty();
    }
}
