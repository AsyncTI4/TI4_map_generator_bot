package ti4.ai.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.fallback.RedistributionGuard.Finish;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.testUtils.BaseTi4Test;

class RedistributionGuardTest extends BaseTi4Test {

    private static final String PREFIX = "FFCC_nekro_";

    private AiTestGame test;
    private final List<String> pressed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
    }

    // The message "your initial command token allocation was 3/3/2 ... final 3/3/1" came from a fallback that
    // pressed Done after a strategy token had been taken out of its pool and not yet placed.
    @Test
    void putsAMissingTokenBackInItsPoolBeforePressingDone() {
        test.nekro.setStrategicCC(1);
        AiPrompt prompt = redistributionPrompt();

        Finish finish = guard(prompt, done(prompt));

        assertThat(finish.pressed()).isTrue();
        assertThat(finish.restored()).isEqualTo(1);
        assertThat(pressed).containsExactly(PREFIX + "increase_strategy_cc", PREFIX + "deleteButtons");
        assertThat(test.nekro.getCCRepresentation()).isEqualTo("3/3/2");
    }

    @Test
    void restoresEveryMissingTokenToThePoolsTheyCameFrom() {
        test.nekro.setStrategicCC(0);
        test.nekro.setFleetCC(2);
        AiPrompt prompt = redistributionPrompt();

        Finish finish = guard(prompt, done(prompt));

        assertThat(finish.restored()).isEqualTo(3);
        assertThat(test.nekro.getCCRepresentation()).isEqualTo("3/3/2");
        assertThat(pressed).endsWith(PREFIX + "deleteButtons");
    }

    @Test
    void usesTheTacticPoolWhenTheOriginalPoolCannotGrow() {
        test.nekro.setStrategicCC(1);
        AiPrompt prompt = redistributionPrompt(
                PREFIX + "increase_tactic_cc", PREFIX + "increase_fleet_cc", PREFIX + "deleteButtons");

        Finish finish = guard(prompt, done(prompt));

        assertThat(finish.pressed()).isTrue();
        assertThat(pressed).containsExactly(PREFIX + "increase_tactic_cc", PREFIX + "deleteButtons");
        assertThat(test.nekro.getCCRepresentation()).isEqualTo("4/3/1");
    }

    @Test
    void doesNotFinishWhenTheTokensCannotBePutBack() {
        test.nekro.setStrategicCC(1);
        AiPrompt prompt = redistributionPrompt(PREFIX + "decrease_tactic_cc", PREFIX + "deleteButtons");

        Finish finish = guard(prompt, done(prompt));

        assertThat(finish.pressed()).isFalse();
        assertThat(pressed).isEmpty();
    }

    @Test
    void pressesDoneStraightAwayWhenNothingIsMissing() {
        test.nekro.setStrategicCC(1);
        test.nekro.setTacticalCC(4);
        AiPrompt prompt = redistributionPrompt();

        Finish finish = guard(prompt, done(prompt));

        assertThat(finish.restored()).isZero();
        assertThat(pressed).containsExactly(PREFIX + "deleteButtons");
    }

    @Test
    void leavesOtherButtonsAlone() {
        test.nekro.setStrategicCC(1);
        AiPrompt prompt = redistributionPrompt();
        PromptButton reset = prompt.buttons().stream()
                .filter(button -> button.customId().endsWith("resetCCs"))
                .findFirst()
                .orElseThrow();

        Finish finish = guard(prompt, reset);

        assertThat(finish.restored()).isZero();
        assertThat(pressed).containsExactly(PREFIX + "resetCCs");
    }

    @Test
    void treatsDoneLosingAsAnIntendedLoss() {
        AiPrompt prompt = AiTestGame.prompt(
                "losing",
                PromptSource.AI_THREAD,
                AiTestGame.NOW,
                List.of(PREFIX + "deleteButtons"),
                List.of("Done Losing Command Tokens"));

        assertThat(RedistributionGuard.finishesRedistribution(prompt.buttons().getFirst()))
                .isFalse();
    }

    private Finish guard(AiPrompt prompt, PromptButton choice) {
        Predicate<PromptButton> press = button -> {
            pressed.add(button.customId());
            apply(button.handlerId());
            return true;
        };
        return RedistributionGuard.pressRestoringTokens(() -> test.game, test.nekro.getUserID(), prompt, choice, press);
    }

    private void apply(String handler) {
        int delta = handler.startsWith("increase") ? 1 : handler.startsWith("decrease") ? -1 : 0;
        if (handler.contains("tactic")) test.nekro.setTacticalCC(test.nekro.getTacticalCC() + delta);
        if (handler.contains("fleet")) test.nekro.setFleetCC(test.nekro.getFleetCC() + delta);
        if (handler.contains("strategy")) test.nekro.setStrategicCC(test.nekro.getStrategicCC() + delta);
    }

    private static PromptButton done(AiPrompt prompt) {
        return prompt.buttons().stream()
                .filter(button -> button.customId().endsWith("deleteButtons"))
                .findFirst()
                .orElseThrow();
    }

    private static AiPrompt redistributionPrompt() {
        return redistributionPrompt(
                PREFIX + "increase_tactic_cc",
                PREFIX + "increase_fleet_cc",
                PREFIX + "increase_strategy_cc",
                PREFIX + "decrease_tactic_cc",
                PREFIX + "decrease_fleet_cc",
                PREFIX + "decrease_strategy_cc",
                PREFIX + "deleteButtons",
                PREFIX + "resetCCs");
    }

    private static AiPrompt redistributionPrompt(String... customIds) {
        List<PromptButton> buttons = new ArrayList<>();
        for (int i = 0; i < customIds.length; i++) {
            String label = customIds[i].endsWith("deleteButtons")
                    ? "Done Redistributing Command Tokens"
                    : customIds[i].endsWith("resetCCs") ? "Reset Command Tokens" : "Option " + i;
            buttons.add(PromptButton.of(i, Button.secondary(customIds[i], label)));
        }
        return new AiPrompt("channel", "tokens", PromptSource.AI_THREAD, "", buttons, AiTestGame.NOW);
    }
}
