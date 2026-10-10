package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.secrets.ActionSecretRules;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Bombardment comes before ground forces are committed: the bot offers "Roll BOMBARDMENT" next to the landing
// buttons, auto-assigns the dice to the first planet with enemy ground forces, and asks the defender to assign
// the hits.
class BombardmentRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private String target;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        target = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", target);
        test.game.setActiveSystem(target);
        test.sol.addPlanet("lodor");
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(tile, "space", test.nekro, UnitType.Carrier, 1);
        test.units(tile, "space", test.nekro, UnitType.Infantry, 2);
    }

    @Test
    void bombardsBeforeLanding() {
        AiPrompt landing = landingPrompt("combatRoll_" + target + "_space_bombardment");

        assertThat(pressedId(TacticalRules.continueAction(test.context(landing)).orElseThrow()))
                .isEqualTo("combatRoll_" + target + "_space_bombardment");
    }

    // Once the dice are rolled it gives the defender time to assign the hits, and lands as soon as they are.
    @Test
    void waitsForTheBombardmentHitsThenLands() {
        AiPrompt landing = landingPrompt("combatRoll_" + target + "_space_bombardment");
        TacticalRules.continueAction(test.context(landing));
        AiPrompt hits = prompt(
                "hits",
                PromptSource.PUBLIC,
                NOW + 10,
                "FFCC_sol_autoAssignGroundHits_lodor_1",
                "getDamageButtons_" + target + "_bombardment");

        assertThat(TacticalRules.continueAction(test.context(landing, hits)).orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);

        tile.removeUnit("lodor", Units.getUnitKey(UnitType.Infantry, test.sol.getColor()), 1);

        assertThat(pressedId(TacticalRules.continueAction(test.context(landing, hits))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_landUnits_" + target + "_1gf_lodor_black");
    }

    // A PDS gives the planet a Planetary Shield, so bombarding it would be illegal.
    @Test
    void leavesAShieldedPlanetAlone() {
        test.units(tile, "lodor", test.sol, UnitType.Pds, 1);
        AiPrompt landing = landingPrompt("combatRoll_" + target + "_space_bombardment");

        assertThat(pressedId(TacticalRules.continueAction(test.context(landing)).orElseThrow()))
                .isNotEqualTo("combatRoll_" + target + "_space_bombardment");
    }

    // With several planets the bot first shows the assignment (auto-assigned to the defended planet), then rolls
    // on "Done Assigning".
    @Test
    void confirmsTheAssignmentInASystemWithSeveralPlanets() {
        AiPrompt landing = landingPrompt("bombardConfirm_combatRoll_" + target + "_space_bombardment");
        assertThat(pressedId(TacticalRules.continueAction(test.context(landing)).orElseThrow()))
                .isEqualTo("bombardConfirm_combatRoll_" + target + "_space_bombardment");

        AiPrompt assignment = prompt(
                "assignment",
                PromptSource.PUBLIC,
                NOW + 10,
                "combatRoll_" + target + "_space_bombardment_deleteTheseButtons");

        assertThat(pressedId(TacticalRules.continueAction(test.context(landing, assignment))
                        .orElseThrow()))
                .isEqualTo("combatRoll_" + target + "_space_bombardment_deleteTheseButtons");
    }

    // A dreadnought rolls one bombardment die hitting on 5 (60%); against a lone infantry that is the chance to
    // make an example of the planet.
    @Test
    void estimatesTheChanceToDestroyEveryDefender() {
        Planet lodor = (Planet) tile.getUnitHolders().get("lodor");

        double chance =
                BombardmentRules.chanceToDestroyAll(test.nekro, Map.of(UnitType.Dreadnought, 1), lodor, test.sol);

        assertThat(chance).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(BombardmentRules.expectedHits(test.nekro, Map.of(UnitType.Dreadnought, 2)))
                .isEqualTo(1);
    }

    // Make an Example of Their World: the bombardment destroyed the last ground forces before any landing.
    @Test
    void scoresMakeAnExampleWhenTheBombardmentDestroysTheLastDefender() {
        test.nekro.setSecret("mew");
        ActionSecretRules.watchBombardment(test.context(), target, "lodor", test.sol);
        tile.removeUnit("lodor", Units.getUnitKey(UnitType.Infantry, test.sol.getColor()), 1);
        String score = "so_score_hand_" + test.nekro.getSecretsUnscored().get("mew");
        AiPrompt buttons = prompt("score", PromptSource.AI_THREAD, NOW, score);

        assertThat(ActionSecretRules.next(test.context(buttons)).map(AiTestGame::pressedId))
                .contains(score);
    }

    // Defenders killed in the ground combat after landing do not count.
    @Test
    void doesNotScoreMakeAnExampleForAGroundCombatWin() {
        test.nekro.setSecret("mew");
        ActionSecretRules.watchBombardment(test.context(), target, "lodor", test.sol);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 1);
        test.game.setStoredValue("combatRoundTrackernekro" + target + "lodor", "1");
        tile.removeUnit("lodor", Units.getUnitKey(UnitType.Infantry, test.sol.getColor()), 1);
        String score = "so_score_hand_" + test.nekro.getSecretsUnscored().get("mew");
        AiPrompt buttons = prompt("score", PromptSource.AI_THREAD, NOW, score);

        assertThat(ActionSecretRules.next(test.context(buttons))).isEmpty();
    }

    private AiPrompt landingPrompt(String bombardButton) {
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + target + " (Lodor).");
        TacticalRules.remember(
                test.context(), new TacticalPlan(TacticalPlan.Kind.ATTACK, target, List.of(), Map.of("lodor", 1), 5.0));
        return prompt(
                "landing",
                PromptSource.PUBLIC,
                NOW,
                bombardButton,
                "FFCC_nekro_landUnits_" + target + "_1gf_lodor_black",
                "FFCC_nekro_doneLanding_" + target);
    }
}
