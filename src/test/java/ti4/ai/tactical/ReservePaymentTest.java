package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.SpendCost;
import ti4.testUtils.BaseTi4Test;

// Paying for units used to fall back on the smallest planet covering the bill whenever the reserve-keeping plan could
// not be pressed, which spent planets the scoring reserve was keeping. It now looks for another payment that keeps
// the reserve, and stops placing units before their cost would break it.
class ReservePaymentTest extends BaseTi4Test {

    private AiTestGame test;

    // Nekro holds Mordai II (4 resources), Abyz (3), Fria (2) and Tarmann (1), all ready.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        test.place("38", "201");
        test.place("22", "202");
        for (String planet : List.of("abyz", "fria", "tarmann")) test.nekro.addPlanet(planet);
    }

    private static AiPrompt payment(String... planets) {
        List<String> ids = new java.util.ArrayList<>();
        for (String planet : planets) ids.add("spend_" + planet + "_res");
        ids.add("deleteButtons_tacticalAction");
        return prompt("payment", PromptSource.PUBLIC, NOW, ids, List.of());
    }

    private static String id(java.util.Optional<PromptButton> button) {
        return button.map(PromptButton::handlerId).orElse("none");
    }

    // Owing 3 with 7 to keep (10 ready): the plan pays with Abyz, but Abyz is not offered. Mordai II, the smallest
    // planet covering the bill, would leave 6; Fria leaves 8 and keeps the reserve.
    @Test
    void findsAnotherPaymentThatKeepsTheReserve() {
        AiPrompt offered = payment("mordaiii", "fria", "tarmann");
        SpendCost reserve = SpendCost.resources(7);

        assertThat(id(PaymentPlanner.choosePlanet(test.game, offered, 3))).isEqualTo("spend_mordaiii_res");
        assertThat(id(PaymentPlanner.keepingReserve(test.game, test.nekro, offered, 3, reserve)))
                .isEqualTo("spend_fria_res");
    }

    // Keeping 9 of 10, no payment of the whole bill of 3 keeps the reserve, but paying Tarmann first does; the old
    // fallback spent Abyz, the smallest planet covering the bill.
    @Test
    void paysPartOfTheBillWithAPlanetTheReserveCanSpare() {
        AiPrompt offered = payment("mordaiii", "abyz", "fria", "tarmann");
        SpendCost reserve = SpendCost.resources(9);

        assertThat(id(PaymentPlanner.choosePlanet(test.game, offered, 3))).isEqualTo("spend_abyz_res");
        assertThat(id(PaymentPlanner.keepingReserve(test.game, test.nekro, offered, 3, reserve)))
                .isEqualTo("spend_tarmann_res");
    }

    // A spare trade good pays when no planet can be spared; one the reserve needs is not.
    @Test
    void paysWithASpareTradeGood() {
        AiPrompt offered = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW,
                List.of("spend_mordaiii_res", "reduceTG_1_res", "deleteButtons_tacticalAction"),
                List.of());
        test.nekro.setTg(1);

        assertThat(id(PaymentPlanner.keepingReserve(test.game, test.nekro, offered, 3, SpendCost.resources(10))))
                .isEqualTo("reduceTG_1_res");
        assertThat(PaymentPlanner.keepingReserve(test.game, test.nekro, offered, 3, SpendCost.resources(11)))
                .isEmpty();
    }

    // With nothing reserved, placing units is never held back.
    @Test
    void placementIsNotHeldBackWithoutAReserve() {
        assertThat(TacticalRules.breaksReserve(test.game, test.nekro, 6)).isFalse();
    }

    // Keeping 8 of 10 for Erect a Monument, a build costing 3 would break it and is not placed; one costing 2 is.
    @Test
    void placementStopsBeforeBreakingTheReserve() {
        test.game.getRevealedPublicObjectives().put("monument", 1);

        assertThat(TacticalRules.breaksReserve(test.game, test.nekro, 3)).isTrue();
        assertThat(TacticalRules.breaksReserve(test.game, test.nekro, 2)).isFalse();
    }
}
