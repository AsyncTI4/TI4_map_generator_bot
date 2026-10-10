package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The Enigmatic Device is purged as a component action for a technology and 6 resources. The bot posts the research
// but does not charge the 6 resources, so the AI plans and pays them itself, through the usual research payment.
class EnigmaticDeviceRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private AiPrompt turn;

    // Sol (an AI here) holds Jord (4/2) and Lodor (3/1): 7 resources, enough for the device, and Antimass Deflectors,
    // which makes Gravity Drive (worth 5, above the 4 it takes to pay for a technology) researchable.
    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.place("01", "304");
        test.place("26", "305");
        sol.addPlanet("jord");
        sol.addPlanet("lodor");
        sol.addRelic("enigmaticdevice");
        test.isActive(sol, "action");
        turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_sol_componentAction", "FFCC_sol_passForRound");
    }

    @Test
    void researchesWithTheDeviceBeforePassing() {
        assertThat(pressedId(RelicActionRules.beforePassing(context(turn), List.of(turn))
                        .orElseThrow()))
                .isEqualTo("FFCC_sol_componentAction");

        AiPrompt menu =
                prompt("menu", PromptSource.PUBLIC, NOW + 1, "FFCC_sol_componentActionRes_relic_enigmaticdevice");
        assertThat(pressedId(RelicActionRules.next(context(menu)).orElseThrow()))
                .isEqualTo("FFCC_sol_componentActionRes_relic_enigmaticdevice");

        AiPrompt get = AiTestGame.withContent(
                prompt("get", PromptSource.PUBLIC, NOW + 2, "acquireATech"),
                sol.getRepresentationUnfogged() + ", you may use the button to research your technology.");
        assertThat(pressedId(RelicActionRules.next(context(get)).orElseThrow())).isEqualTo("acquireATech");

        AiPrompt types = prompt(
                "types",
                PromptSource.AI_THREAD,
                NOW + 3,
                "FFCC_sol_getAllTechOfType_biotic",
                "FFCC_sol_getAllTechOfType_propulsion",
                "FFCC_sol_getAllTechOfType_unitupgrade");
        assertThat(pressedId(RelicActionRules.next(context(types)).orElseThrow()))
                .isEqualTo("FFCC_sol_getAllTechOfType_propulsion");

        AiPrompt list = prompt("list", PromptSource.AI_THREAD, NOW + 4, "FFCC_sol_getTech_det", "FFCC_sol_getTech_gd");
        assertThat(pressedId(StrategyCardRules.chooseTechnology(context(list)).orElseThrow()))
                .isEqualTo("FFCC_sol_getTech_gd");
        assertThat(PaymentRules.isPending(context())).isTrue();
    }

    // Without 6 resources to spare there is nothing to research with.
    @Test
    void doesNotResearchWithoutTheResources() {
        sol.getPlanets().forEach(sol::exhaustPlanet);

        assertThat(RelicActionRules.beforePassing(context(turn), List.of(turn))).isEmpty();
    }

    // Nekro cannot research: Propagation turns the research into 3 command tokens (6, above the 3 that 6 resources are
    // worth). The bot posts the tokens with an "Exhaust Planets" button but charges nothing, so the AI presses it
    // before taking the tokens (the brain would otherwise close the message), and pays 6 resources.
    @Test
    void nekroTakesTheTokensAndPaysSixResources() {
        nekroWithSixResources();
        AiPrompt nekroTurn =
                prompt("nekroTurn", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
        assertThat(pressedId(RelicActionRules.beforePassing(test.context(nekroTurn), List.of(nekroTurn))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");

        AiPrompt menu =
                prompt("menu", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_componentActionRes_relic_enigmaticdevice");
        assertThat(pressedId(RelicActionRules.next(test.context(menu)).orElseThrow()))
                .isEqualTo("FFCC_nekro_componentActionRes_relic_enigmaticdevice");

        AiPrompt tokens = AiTestGame.withContent(
                prompt(
                        "tokens",
                        PromptSource.PUBLIC,
                        NOW + 2,
                        List.of(
                                "FFCC_nekro_increase_tactic_cc",
                                "FFCC_nekro_deleteButtons",
                                "FFCC_nekro_nekroTechExhaust"),
                        List.of("Gain 1 Tactic Token", "Done Gaining Command Tokens", "Exhaust Planets")),
                "because of **Propagation**, you instead gain 3 command tokens");
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        assertThat(pressedId(new NekroBrain().decide(test.context(tokens)))).isEqualTo("FFCC_nekro_nekroTechExhaust");
        assertThat(PaymentRules.isPending(test.context())).isTrue();

        AiPrompt payment = prompt(
                "payment",
                PromptSource.AI_THREAD,
                NOW + 3,
                List.of("spend_mordaiii_restech", "spend_lodor_restech", PaymentRules.TECHNOLOGY_DONE),
                List.of("Mordai II", "Lodor", "Done Exhausting Planets"));
        assertThat(PaymentRules.pay(test.context(payment))).isPresent();
    }

    // Without room for 3 tokens in reinforcements, or without 6 resources after the reserve, the device stays.
    @Test
    void nekroLeavesTheDeviceAloneWithoutRoomOrResources() {
        nekroWithSixResources();
        AiPrompt nekroTurn =
                prompt("nekroTurn", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
        test.nekro.setTacticalCC(9);
        test.nekro.setFleetCC(4);
        test.nekro.setStrategicCC(2);
        assertThat(RelicActionRules.beforePassing(test.context(nekroTurn), List.of(nekroTurn)))
                .isEmpty();

        test.nekro.setTacticalCC(3);
        test.nekro.exhaustPlanet("lodor");
        assertThat(RelicActionRules.beforePassing(test.context(nekroTurn), List.of(nekroTurn)))
                .isEmpty();
    }

    private void nekroWithSixResources() {
        test.nekroHome();
        test.place("26", "302");
        test.nekro.addPlanet("lodor");
        test.nekro.addRelic("enigmaticdevice");
        test.aiIsActive("action");
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sol, Set.of(), NOW, prompts);
    }
}
