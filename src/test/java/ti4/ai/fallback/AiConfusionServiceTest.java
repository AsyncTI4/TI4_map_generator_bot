package ti4.ai.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.testUtils.BaseTi4Test;

class AiConfusionServiceTest extends BaseTi4Test {

    // For private choices the AI never shows its options to the table; it only ever declines.
    @Test
    void safeDefaultPrefersDecliningOptions() {
        AiPrompt prompt = prompt("playAC_sabo", "FFCC_nekro_deleteButtons", "FFCC_nekro_exhaustAgent_nekroagent");

        assertThat(AiConfusionService.safeDefault(prompt))
                .map(PromptButton::customId)
                .contains("FFCC_nekro_deleteButtons");
    }

    // Without a declining option there is no safe choice: never press something at random (it could play a card).
    @Test
    void safeDefaultIsEmptyWithoutADecliningOption() {
        AiPrompt prompt = prompt("ac_play_from_hand_12", "getDiscardButtonsACs");

        assertThat(AiConfusionService.safeDefault(prompt)).isEmpty();
    }

    // Buttons that open a form can't be completed by the AI, so they are never a safe choice.
    @Test
    void safeDefaultSkipsButtonsThatOpenAForm() {
        AiPrompt prompt = prompt("editRoundSummary_3~MDL", "declineSomething~MDL");

        assertThat(AiConfusionService.safeDefault(prompt)).isEmpty();
    }

    @Test
    void claimingADelegationSucceedsOnlyOnce() {
        assertThat(AiConfusionService.claim("delegation-message-1")).isTrue();
        assertThat(AiConfusionService.claim("delegation-message-1")).isFalse();
    }

    private static AiPrompt prompt(String... customIds) {
        List<PromptButton> buttons = new ArrayList<>();
        for (int i = 0; i < customIds.length; i++) {
            buttons.add(PromptButton.of(i, Button.secondary(customIds[i], "Option " + i)));
        }
        return new AiPrompt("channel", "message", AiPrompt.PromptSource.AI_THREAD, "", buttons, 0L);
    }
}
