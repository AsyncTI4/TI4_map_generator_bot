package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Unit production is paid with resources. The Xxcha hero adds each planet's influence to its resources; the
// breakthrough lets a planet pay its larger value. The payment buttons stay the same, so the bot must choose the
// planets by what the engine will count.
class XxchaPaymentTest extends BaseTi4Test {

    private AiTestGame test;
    private Player xxcha;

    // Xxcha holds Archon Ren (2/3), Archon Tau (1/1) and Centauri (1/3), all ready.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.isActive(xxcha, "action");
        test.place("14", "301");
        test.place("34", "201");
        for (String planet : List.of("archonren", "archontau", "centauri")) xxcha.addPlanet(planet);
    }

    private static AiPrompt payment(String... planets) {
        List<String> ids = new java.util.ArrayList<>();
        for (String planet : planets) ids.add("spend_" + planet + "_res");
        ids.add("deleteButtons_tacticalAction");
        return prompt("payment", PromptSource.PUBLIC, NOW, ids, List.of());
    }

    private static String id(Optional<PromptButton> button) {
        return button.map(PromptButton::handlerId).orElse("none");
    }

    private void unlockHero() {
        xxcha.addLeader("xxchahero");
        xxcha.getLeader("xxchahero").orElseThrow().setLocked(false);
    }

    private void unlockBreakthrough() {
        xxcha.addBreakthrough("xxchabt");
        xxcha.setBreakthroughUnlocked("xxchabt", true);
    }

    @Test
    void withoutThePoolingTheBiggestResourcePlanetPaysFirst() {
        AiPrompt offered = payment("archonren", "archontau", "centauri");

        assertThat(id(PaymentPlanner.choosePlanet(test.game, xxcha, offered, 4)))
                .isEqualTo("spend_archonren_res");
    }

    // Owing 4: Centauri (1/3) counts 4 with the hero and is the smallest planet that covers it, so Archon Ren (5)
    // stays ready.
    @Test
    void theHeroPaysWithTheSmallestPlanetWhoseCombinedValueCoversTheBill() {
        unlockHero();
        AiPrompt offered = payment("archonren", "archontau", "centauri");

        assertThat(id(PaymentPlanner.choosePlanet(test.game, xxcha, offered, 4)))
                .isEqualTo("spend_centauri_res");
    }

    // Centauri is 1 resource and 3 influence: with the breakthrough it covers a bill of 3 on its own, which the
    // 1-resource Archon Tau cannot.
    @Test
    void theBreakthroughSpendsInfluenceAsResources() {
        unlockBreakthrough();
        AiPrompt offered = payment("archontau", "centauri");

        assertThat(id(PaymentPlanner.choosePlanet(test.game, xxcha, offered, 3)))
                .isEqualTo("spend_centauri_res");
    }

    @Test
    void theHeroKeepsTheReserveWhenChoosingWhichPlanetToExhaust() {
        unlockHero();
        AiPrompt offered = payment("archonren", "archontau", "centauri");

        assertThat(id(PaymentPlanner.keepingReserve(
                        test.game, xxcha, offered, 4, ti4.ai.scoring.SpendCost.resources(7))))
                .isEqualTo("spend_centauri_res");
    }
}
