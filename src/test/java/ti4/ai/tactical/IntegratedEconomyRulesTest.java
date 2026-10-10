package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class IntegratedEconomyRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", position);
        test.nekro.addTech("ie");
        test.nekro.addPlanet("lodor");
        test.aiIsActive("action");
    }

    // Taking Lodor (3 resources) with Integrated Economy lets the seat produce up to 3 resources of units there. It
    // builds a mech and two infantry to hold the new planet, then pays for them.
    @Test
    void buildsGroundForcesOnAPlanetItJustTook() {
        AiPrompt offer = offer();
        assertThat(pressedId(IntegratedEconomyRules.next(test.context(offer)).orElseThrow()))
                .isEqualTo("integratedBuild_lodor");

        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW + 5,
                List.of(
                        "FFCC_nekro_place_mech_lodor",
                        "FFCC_nekro_place_infantry_lodor",
                        "FFCC_nekro_place_2gf_lodor",
                        "FFCC_nekro_deleteButtons_integratedlodor_" + position),
                List.of("Produce Mech", "Produce Infantry", "Produce 2 Infantry", "Done Producing Units"));
        assertThat(pressedId(IntegratedEconomyRules.next(test.context(offer, production))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_place_mech_lodor");

        test.nekro.produceUnit("mf_" + position + "_lodor");
        assertThat(pressedId(IntegratedEconomyRules.next(test.context(offer, production))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_place_2gf_lodor");

        test.nekro.produceUnit("gf_" + position + "_lodor");
        test.nekro.produceUnit("gf_" + position + "_lodor");
        assertThat(pressedId(IntegratedEconomyRules.next(test.context(offer, production))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons_integratedlodor_" + position);

        test.game.setStoredValue("producedUnitCostFornekro", "3");
        AiPrompt payment = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW + 10,
                List.of("spend_mordaiii_res", "deleteButtons_integratedlodor"),
                List.of("Mordai II", "Done Exhausting Planets"));
        assertThat(pressedId(IntegratedEconomyRules.next(test.context(offer, payment))
                        .orElseThrow()))
                .isEqualTo("spend_mordaiii_res");
    }

    // With every planet already spent there is nothing to pay with, so the offer is declined.
    @Test
    void declinesWithNothingLeftToSpend() {
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);

        assertThat(pressedId(IntegratedEconomyRules.next(test.context(offer())).orElseThrow()))
                .isEqualTo("deleteButtons");
    }

    // The offer is answered once; pressing the same message again would be a duplicate click.
    @Test
    void answersEachOfferOnce() {
        AiPrompt offer = offer();
        assertThat(IntegratedEconomyRules.next(test.context(offer))).isPresent();

        assertThat(IntegratedEconomyRules.next(test.context(offer))).isEmpty();
    }

    // Without Integrated Economy the offer belongs to someone else's flow and is left alone.
    @Test
    void ignoresTheOfferWithoutTheTechnology() {
        test.nekro.removeTech("ie");

        assertThat(IntegratedEconomyRules.next(test.context(offer()))).isEmpty();
    }

    private AiPrompt offer() {
        return AiTestGame.withContent(
                prompt(
                        "offer",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("integratedBuild_lodor", "deleteButtons"),
                        List.of("Integrated on Lodor", "Decline")),
                test.nekro.getRepresentation()
                        + " Click the button to resolve an _Integrated Economy_ build on Lodor.");
    }
}
