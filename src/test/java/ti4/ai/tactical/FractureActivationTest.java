package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The system picker offers the Fracture's systems under "Corners & Fracture" (ring_corners) or, in distance mode,
// among the tiles at a distance; the AI finds a planned Fracture system through either.
class FractureActivationTest extends BaseTi4Test {

    private FractureScenario fracture;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
        fracture.board.test.aiIsActive("action");
        fracture.ships("frac3", UnitType.Dreadnought, 5);
        fracture.ships("frac3", UnitType.Carrier, 2);
        fracture.ships("frac3", UnitType.Infantry, 4);
    }

    @Test
    void theCornersRingIsWhereFractureSystemsAreFound() {
        assertThat(TacticalRules.ringButtons("frac4")).containsExactly("ring_corners");
        assertThat(TacticalRules.ringButtons("frac10")).containsExactly("ring_corners");
    }

    @Test
    void theRealCornersRingOffersTheFractureSystems() {
        List<String> offered =
                ButtonHelper.getTileInARing(fracture.nekro(), fracture.board.test.game, "ring_corners").stream()
                        .map(button -> button.getCustomId())
                        .toList();

        assertThat(offered).anyMatch(id -> id.endsWith("ringTile_frac4"));
    }

    @Test
    void opensTheCornersRingToFindAPlannedFractureSystem() {
        TacticalRules.remember(
                fracture.board.test.context(), new TacticalPlan(Kind.ATTACK, "frac4", List.of(), Map.of(), 9.0));
        AiPrompt rings = prompt("rings", PromptSource.PUBLIC, NOW, "ring_1", "ring_2", "ring_3", "ring_corners");

        assertThat(pressedId(
                        TacticalRules.start(fracture.board.test.context(rings)).orElseThrow()))
                .isEqualTo("ring_corners");
    }

    @Test
    void activatesAPlannedFractureSystemOnceItIsOffered() {
        TacticalRules.remember(
                fracture.board.test.context(), new TacticalPlan(Kind.ATTACK, "frac4", List.of(), Map.of(), 9.0));
        AiPrompt corners = prompt(
                "corners",
                PromptSource.PUBLIC,
                NOW,
                "ringTile_frac1",
                "ringTile_frac4",
                "ringTile_tl",
                "ChooseDifferentDestination");

        assertThat(pressedId(TacticalRules.start(fracture.board.test.context(corners))
                        .orElseThrow()))
                .isEqualTo("ringTile_frac4");
    }

    @Test
    void plansTheActivationOfStyxFromTheTurnButtons() {
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction");

        assertThat(pressedId(
                        TacticalRules.start(fracture.board.test.context(turn)).orElseThrow()))
                .isEqualTo("FFCC_nekro_tacticalAction");
        assertThat(TacticalRules.rememberedPlan(fracture.board.test.context()).map(TacticalPlan::target))
                .contains("frac4");
    }
}
