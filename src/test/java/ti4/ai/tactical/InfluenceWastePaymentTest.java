package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.testUtils.BaseTi4Test;

// Paying for units throws away the influence of every planet it exhausts. At 2 resources = 3 influence, a 2/3 planet
// like Lirta IV is worth keeping for Leadership or the custodians when a resource planet pays the same bill.
class InfluenceWastePaymentTest extends BaseTi4Test {

    private AiTestGame test;

    // Abyz is 3/0, Fria 2/0, Lirta IV 2/3 and Tarmann 1/1.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        test.place("38", "201");
        test.place("35", "202");
        test.place("22", "203");
        for (String planet : List.of("abyz", "fria", "lirtaiv", "tarmann")) test.nekro.addPlanet(planet);
    }

    private static AiPrompt payment(String... planets) {
        List<String> ids = new ArrayList<>();
        for (String planet : planets) ids.add("spend_" + planet + "_res");
        ids.add("deleteButtons_tacticalAction");
        return prompt("payment", PromptSource.PUBLIC, NOW, ids, List.of());
    }

    private static String id(Optional<PromptButton> button) {
        return button.map(PromptButton::handlerId).orElse("none");
    }

    @Test
    void paysWithTheResourcePlanetWhenBothCoverTheBillExactly() {
        assertThat(id(PaymentPlanner.choosePlanet(test.game, payment("lirtaiv", "fria"), 2)))
                .isEqualTo("spend_fria_res");
    }

    // Abyz overpays by 1 resource; Lirta IV would throw away 3 influence, worth 2 resources.
    @Test
    void overpaysWithAResourcePlanetRatherThanSpendLirtaIv() {
        assertThat(id(PaymentPlanner.choosePlanet(test.game, payment("lirtaiv", "abyz"), 2)))
                .isEqualTo("spend_abyz_res");
    }

    // With no single planet covering the bill, the largest resource planet pays first, and of two equal ones the one
    // with less influence.
    @Test
    void startsALargerBillWithTheResourcePlanet() {
        assertThat(id(PaymentPlanner.choosePlanet(test.game, payment("lirtaiv", "fria", "tarmann"), 5)))
                .isEqualTo("spend_fria_res");
    }

    @Test
    void namesThePlanetOrTheTradeGoodItPaysWith() {
        AiPrompt offered = prompt("payment", PromptSource.PUBLIC, NOW, "spend_fria_res", "reduceTG_1_res");

        assertThat(PaymentPlanner.describe(offered.enabledButtons().get(0))).isEqualTo("fria");
        assertThat(PaymentPlanner.describe(offered.enabledButtons().get(1))).isEqualTo("a trade good");
    }
}
