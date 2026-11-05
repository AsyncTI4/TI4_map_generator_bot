package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

// The AI waits 10 to 40 minutes before passing on a Sabotage window, so a human deciding whether to Sabotage cannot
// learn early that the AI players hold none. Once every human has answered, that wait hides nothing and only stalls
// the card, so the AI passes within seconds.
class SabotagePassDelayTest extends BaseTi4Test {

    private static final long A_MINUTE_LATER = NOW + 60_000L;

    private AiTestGame test;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        messages = Mockito.mockStatic(GameMessageManager.class);
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void reacted(String... factions) {
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of(factions)), 0L)));
    }

    private static AiPrompt industrialInitiativeByLetnev() {
        return prompt("window", PromptSource.PUBLIC, NOW, "no_sabotage", "sabotage_ac_Industrial Initiative_letnev");
    }

    private Optional<AiDecision> aMinuteLater() {
        return SabotageRules.answerWindows(
                test.contextFor(test.nekro, Set.of(), A_MINUTE_LATER, industrialInitiativeByLetnev()));
    }

    @Test
    void keepsWaitingWhileAHumanHasNotAnswered() {
        reacted();

        assertThat(aMinuteLater()).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void passesQuicklyOnceEveryHumanHasAnswered() {
        reacted(test.sol.getFaction());

        assertThat(aMinuteLater().map(AiTestGame::pressedId)).contains("no_sabotage");
    }
}
