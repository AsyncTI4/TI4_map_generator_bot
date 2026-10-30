package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promissory.NoteGiving;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

/**
 * The bot asks the AI to pick a promissory note to hand over in two ways: after a deal it accepted asked for "a
 * promissory note to be decided", and when an action card forces a note out of it. Nekro holds its Ceasefire (11),
 * Political Secret (12), Support for the Throne (13) and Alliance (14).
 */
class ActionCardResponsesTest extends BaseTi4Test {

    private static final String CHOOSE = ", please choose the promissory note you wish to send.";
    private static final String FORCED = ", you are being forced to give a promissory note to Sol. Please choose which"
            + " promissory note you wish to send.";

    private AiTestGame test;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekro.setPromissoryNote("black_cf", 11);
        test.nekro.setPromissoryNote("black_ps", 12);
        test.nekro.setPromissoryNote("black_sftt", 13);
        test.nekro.setPromissoryNote("black_an", 14);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getAll(anyString(), eq(GameMessageType.ACTION_CARD)))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    /** A Sabotage window nobody has answered yet: an action card may still be cancelled. */
    private void sabotageWindowOpen() {
        messages.when(() -> GameMessageManager.getAll(anyString(), eq(GameMessageType.ACTION_CARD)))
                .thenReturn(List.of(new GameMessage("window", GameMessageType.ACTION_CARD, new LinkedHashSet<>(), 0L)));
    }

    private AiPrompt notePrompt(String text, String... ids) {
        return AiTestGame.withContent(
                prompt("choose", PromptSource.AI_THREAD, NOW, ids), test.nekro.getRepresentation() + text);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // After accepting a deal for "any note", the AI hands over the note it priced the deal with.
    @Test
    void givesTheNoteItPricedForADeal() {
        NoteGiving.owe(test.context(), "sol", "black_ps");
        AiPrompt choose = notePrompt(CHOOSE, "naaluHeroSend_sol_11", "naaluHeroSend_sol_12", "naaluHeroSend_sol_13");

        assertThat(pressed(ActionCardResponses.respond(test.context(choose)))).isEqualTo("naaluHeroSend_sol_12");
    }

    // Without a remembered note it gives the least harmful one, and keeps Support for the Throne and its Alliance
    // while anything else is on offer.
    @Test
    void otherwiseGivesTheLeastHarmfulNote() {
        AiPrompt choose = notePrompt(CHOOSE, "naaluHeroSend_sol_13", "naaluHeroSend_sol_14", "naaluHeroSend_sol_12");

        assertThat(pressed(ActionCardResponses.respond(test.context(choose)))).isEqualTo("naaluHeroSend_sol_12");
    }

    // This prompt follows a deal, not an action card, so an open Sabotage window does not hold it up.
    @Test
    void doesNotWaitForSabotageAfterADeal() {
        sabotageWindowOpen();
        AiPrompt choose = notePrompt(CHOOSE, "naaluHeroSend_sol_11", "naaluHeroSend_sol_12");

        assertThat(pressed(ActionCardResponses.respond(test.context(choose)))).isEqualTo("naaluHeroSend_sol_11");
    }

    // Diplomatic Pressure can still be sabotaged, so the forced give waits until the window closes.
    @Test
    void stillWaitsForSabotageWhenForcedByAnActionCard() {
        AiPrompt forced = notePrompt(FORCED, "naaluHeroSend_sol_11", "naaluHeroSend_sol_12");
        sabotageWindowOpen();

        assertThat(ActionCardResponses.respond(test.context(forced))).isEmpty();

        messages.when(() -> GameMessageManager.getAll(anyString(), eq(GameMessageType.ACTION_CARD)))
                .thenReturn(List.of());
        assertThat(pressed(ActionCardResponses.respond(test.context(forced)))).isEqualTo("naaluHeroSend_sol_11");
    }
}
