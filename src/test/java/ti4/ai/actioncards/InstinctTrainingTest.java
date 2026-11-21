package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

// Instinct Training cancels another player's action card for one strategy token and an exhaust, so it is a Sabotage
// that costs a token instead of a card. The engine offers it in the Xxcha player's own thread, beside the public
// Sabotage window.
class InstinctTrainingTest extends BaseTi4Test {

    private static final long LATE = NOW + 45 * 60_000L;
    private static final String PASS = "no_sabotage";

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.addTech("it");
        table.nekro.setStrategicCC(2);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(
                        new GameMessage(call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(), 0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private static AiPrompt window(String title) {
        return prompt("window-" + title, PromptSource.PUBLIC, NOW, PASS, "sabotage_ac_" + title + "_sol");
    }

    private static AiPrompt instinct(String title) {
        return prompt(
                "instinct-" + title, PromptSource.AI_THREAD, NOW, "sabotage_xxcha_" + title + "_sol", "deleteButtons");
    }

    private String answer(String title) {
        return pressedId(SabotageRules.answerWindows(table.test.contextAt(LATE, window(title), instinct(title)))
                .orElseThrow());
    }

    private void solIsAMatchPointContender() {
        table.points(table.sol, 8);
    }

    // Overrule on Imperial from a player a point and a half from winning is worth a strategy token to stop.
    @Test
    void cancelsACardThatThreatensTheGameWithInstinctTraining() {
        solIsAMatchPointContender();

        assertThat(answer("Overrule")).isEqualTo("sabotage_xxcha_Overrule_sol");
    }

    @Test
    void passesOnACardNotWorthAToken() {
        assertThat(answer("Mining Initiative")).isEqualTo(PASS);
    }

    // The token is cheaper than a held Sabotage, so the card is kept for a play the token cannot answer.
    @Test
    void spendsTheTokenBeforeItsSabotageCard() {
        solIsAMatchPointContender();
        table.nekro.setActionCard("sabo1", 1);

        assertThat(answer("Overrule")).isEqualTo("sabotage_xxcha_Overrule_sol");
    }

    @Test
    void fallsBackToItsSabotageCardWhenThereIsNoTokenToSpend() {
        solIsAMatchPointContender();
        table.nekro.setActionCard("sabo1", 1);
        table.nekro.setStrategicCC(0);

        assertThat(answer("Overrule")).isEqualTo("sabotage_ac_Overrule_sol");
    }

    @Test
    void fallsBackToItsSabotageCardWhenInstinctTrainingIsExhausted() {
        solIsAMatchPointContender();
        table.nekro.setActionCard("sabo1", 1);
        table.nekro.exhaustTech("it");

        assertThat(answer("Overrule")).isEqualTo("sabotage_ac_Overrule_sol");
    }

    @Test
    void passesWhenNeitherATokenNorASabotageIsLeft() {
        solIsAMatchPointContender();
        table.nekro.setStrategicCC(0);

        assertThat(answer("Overrule")).isEqualTo(PASS);
    }

    // Answering once: after Instinct Training the seat does not also play a Sabotage on the same window, it passes so
    // the window can close.
    @Test
    void passesOnTheWindowAfterUsingInstinctTraining() {
        solIsAMatchPointContender();
        table.nekro.setActionCard("sabo1", 1);
        AiPrompt window = window("Overrule");
        AiPrompt instinct = instinct("Overrule");

        String first = pressedId(SabotageRules.answerWindows(table.test.contextAt(LATE, window, instinct))
                .orElseThrow());
        String second = pressedId(
                SabotageRules.answerWindows(table.test.contextFor(table.nekro, Set.of(), LATE, window, instinct))
                        .orElseThrow());

        assertThat(first).isEqualTo("sabotage_xxcha_Overrule_sol");
        assertThat(second).isEqualTo(PASS);
    }

    @Test
    void doesNotPressInstinctTrainingTwice() {
        solIsAMatchPointContender();
        AiPrompt window = window("Overrule");
        AiPrompt instinct = instinct("Overrule");
        String key =
                ti4.ai.brain.AiTurnContext.pressKey(instinct, instinct.buttons().getFirst());

        String decision = pressedId(
                SabotageRules.answerWindows(table.test.contextFor(table.nekro, Set.of(key), LATE, window, instinct))
                        .orElseThrow());

        assertThat(decision).isEqualTo(PASS);
    }

    @Test
    void ignoresInstinctTrainingOfAnotherCard() {
        solIsAMatchPointContender();
        AiPrompt other = instinct("Mining Initiative");

        String decision = pressedId(SabotageRules.answerWindows(table.test.contextAt(LATE, window("Overrule"), other))
                .orElseThrow());

        assertThat(decision).isEqualTo(PASS);
    }
}
