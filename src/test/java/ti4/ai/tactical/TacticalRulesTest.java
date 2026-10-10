package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Each test sets up the game state the bot would be in at one step of a tactical action and checks the single
// button the AI presses next.
class TacticalRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String target;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        target = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", target);
        test.aiIsActive("action");
    }

    @Test
    void startsATacticalActionFromTheTurnButtons() {
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_strategicAction_1");

        assertThat(pressedId(TacticalRules.start(test.context(turn)).orElseThrow()))
                .isEqualTo("FFCC_nekro_tacticalAction");
    }

    // The system picker names no player; the AI recognises it because it appears during its own turn.
    @Test
    void activatesThePlannedSystemAndRemembersThePlan() {
        AiPrompt picker = prompt("picker", PromptSource.PUBLIC, NOW, "ringTile_" + target, "getTilesThisFarAway_2");

        assertThat(pressedId(TacticalRules.start(test.context(picker)).orElseThrow()))
                .isEqualTo("ringTile_" + target);
        assertThat(test.memory.get(TacticalRules.PLAN_KEY + test.context().turnKey()))
                .isPresent();
    }

    // A picker with more than 25 systems is split over several messages. The planned system can be in an earlier
    // message than the newest one, which only holds the rest of the list and the distance buttons.
    @Test
    void findsThePlannedSystemInAnEarlierPartOfASplitPicker() {
        AiPrompt firstPart =
                prompt("picker-1", PromptSource.PUBLIC, NOW - 100, "getTilesThisFarAway_0", "ringTile_" + target);
        AiPrompt secondPart = prompt("picker-2", PromptSource.PUBLIC, NOW, "ringTile_000", "getTilesThisFarAway_2");

        assertThat(pressedId(
                        TacticalRules.start(test.context(firstPart, secondPart)).orElseThrow()))
                .isEqualTo("ringTile_" + target);
    }

    // When the planned system never shows up, the AI picks the best system the picker does offer instead of handing
    // the picker to the table, where a self-play game would keep paging through distances forever.
    @Test
    void activatesTheBestOfferedSystemWhenThePlannedOneCannotBeFound() {
        TacticalRules.remember(test.context(), new TacticalPlan(Kind.EXPAND, "999", List.of(), Map.of(), 2.0));
        test.memory.put(TacticalRules.PICKER_PRESSES_KEY + test.context().turnKey(), "4");
        AiPrompt picker = prompt("picker", PromptSource.PUBLIC, NOW, "ringTile_" + target, "getTilesThisFarAway_2");

        assertThat(pressedId(TacticalRules.start(test.context(picker)).orElseThrow()))
                .isEqualTo("ringTile_" + target);
        assertThat(TacticalRules.rememberedPlan(test.context()).map(TacticalPlan::target))
                .contains(target);
    }

    @Test
    void movesThePlannedUnitsOneAtATimeThenConcludes() {
        activate();
        AiPrompt list = prompt(
                "list",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalMoveFrom_" + AiTestGame.HOME,
                "FFCC_nekro_concludeMove_" + target);
        assertThat(pressedId(TacticalRules.continueAction(test.context(list)).orElseThrow()))
                .isEqualTo("FFCC_nekro_tacticalMoveFrom_" + AiTestGame.HOME);

        AiPrompt units = prompt(
                "units",
                PromptSource.PUBLIC,
                NOW + 10,
                "FFCC_nekro_unitTacticalMove_301_1_cv_black",
                "FFCC_nekro_unitTacticalMove_301_1_dn_black",
                "FFCC_nekro_unitTacticalMove_301_1_gf_mordaiii_black",
                "FFCC_nekro_unitTacticalMove_301_2_gf_mordaiii_black",
                "FFCC_nekro_doneWithOneSystem_301");
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(list, units)).orElseThrow()))
                .isEqualTo("FFCC_nekro_unitTacticalMove_301_1_cv_black");

        stage("301-space", UnitType.Carrier, 1);
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(list, units)).orElseThrow()))
                .isEqualTo("FFCC_nekro_unitTacticalMove_301_1_gf_mordaiii_black");

        stage("301-mordaiii", UnitType.Infantry, 1);
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(list, units)).orElseThrow()))
                .isEqualTo("FFCC_nekro_doneWithOneSystem_301");

        AiPrompt backToList = prompt(
                "units",
                PromptSource.PUBLIC,
                NOW + 10,
                "FFCC_nekro_tacticalMoveFrom_301",
                "FFCC_nekro_concludeMove_" + target);
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(backToList)).orElseThrow()))
                .isEqualTo("FFCC_nekro_concludeMove_" + target);
    }

    @Test
    void landsThenFinishesLandingThenConcludes() {
        activate();
        Tile tile = test.game.getTileByPosition(target);
        test.units(tile, "space", test.nekro, UnitType.Carrier, 1);
        test.units(tile, "space", test.nekro, UnitType.Infantry, 1);
        AiPrompt landing = prompt(
                "landing",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_landUnits_" + target + "_1gf_lodor_black",
                "FFCC_nekro_doneLanding_" + target);
        assertThat(pressedId(TacticalRules.continueAction(test.context(landing)).orElseThrow()))
                .isEqualTo("FFCC_nekro_landUnits_" + target + "_1gf_lodor_black");

        tile.removeUnit("space", Units.getUnitKey(UnitType.Infantry, "black"), 1);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 1);
        assertThat(pressedId(TacticalRules.continueAction(test.context(landing)).orElseThrow()))
                .isEqualTo("FFCC_nekro_doneLanding_" + target);

        AiPrompt conclude = prompt("conclude", PromptSource.PUBLIC, NOW + 10, "FFCC_nekro_doneWithTacticalAction");
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(conclude)).orElseThrow()))
                .isEqualTo("FFCC_nekro_doneWithTacticalAction");
    }

    // Mechs are ground forces too: with only a mech aboard, the mech lands.
    @Test
    void landsAMechWhenNoInfantryIsAboard() {
        activate();
        Tile tile = test.game.getTileByPosition(target);
        test.units(tile, "space", test.nekro, UnitType.Carrier, 1);
        test.units(tile, "space", test.nekro, UnitType.Mech, 1);
        AiPrompt landing = prompt(
                "landing",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_landUnits_" + target + "_1mf_lodor_black",
                "FFCC_nekro_doneLanding_" + target);

        assertThat(pressedId(TacticalRules.continueAction(test.context(landing)).orElseThrow()))
                .isEqualTo("FFCC_nekro_landUnits_" + target + "_1mf_lodor_black");
    }

    // A carrier that brought spare infantry lands only the planned one and keeps the rest aboard for its next
    // expansion.
    @Test
    void landsOnlyThePlannedInfantryAndKeepsTheRestAboard() {
        activate();
        Tile tile = test.game.getTileByPosition(target);
        test.units(tile, "space", test.nekro, UnitType.Carrier, 1);
        test.units(tile, "space", test.nekro, UnitType.Infantry, 2);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 1);
        AiPrompt landing = prompt(
                "landing",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_landUnits_" + target + "_1gf_lodor_black",
                "FFCC_nekro_landUnits_" + target + "_2gf_lodor_black",
                "FFCC_nekro_doneLanding_" + target);

        assertThat(pressedId(TacticalRules.continueAction(test.context(landing)).orElseThrow()))
                .isEqualTo("FFCC_nekro_doneLanding_" + target);
    }

    // Landing waits while enemy ships are still in the active system.
    @Test
    void waitsForSpaceCombatBeforeLanding() {
        activate();
        Tile tile = test.game.getTileByPosition(target);
        test.units(tile, "space", test.nekro, UnitType.Carrier, 1);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        AiPrompt landing = prompt(
                "landing",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_landUnits_" + target + "_1gf_lodor_black",
                "FFCC_nekro_doneLanding_" + target);

        assertThat(TacticalRules.continueAction(test.context(landing)).orElseThrow())
                .isInstanceOf(ti4.ai.brain.AiDecision.Wait.class);
    }

    // A tactical action into its home dock builds the remembered plan, then pays with the fewest wasted resources.
    @Test
    void buildsTheRememberedPlanThenPays() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        test.memory.put(
                TacticalRules.BUILD_KEY + test.context().turnKey(),
                new ProductionPlanner.BuildPlan(List.of(new ProductionPlanner.BuildOrder(
                                "carrier", AiTestGame.HOME, UnitType.Carrier, 1, 3.0, 2.5, false)))
                        .encode());
        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "FFCC_nekro_place_carrier_301",
                        "FFCC_nekro_place_2gf_mordaiii",
                        "FFCC_nekro_deleteButtons_tacticalAction_301"),
                List.of("Produce Carrier", "Produce 2 Infantry", "Done Producing Units"));
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("FFCC_nekro_place_carrier_301");

        test.nekro.produceUnit("cv_301_space");
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons_tacticalAction_301");

        test.game.setStoredValue("producedUnitCostFornekro", "3");
        AiPrompt payment = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW + 10,
                List.of("spend_mordaiii_res", "reduceTG_1_res", "deleteButtons_tacticalAction"),
                List.of("Mordai II", "Spend 1 TG", "Done Exhausting Planets"));
        assertThat(pressedId(TacticalRules.continueAction(test.context(payment)).orElseThrow()))
                .isEqualTo("spend_mordaiii_res");

        test.nekro.addSpentThing("mordaiii");
        assertThat(pressedId(TacticalRules.continueAction(test.context(payment)).orElseThrow()))
                .isEqualTo("deleteButtons_tacticalAction");
    }

    // With two unit upgrades AI Development Algorithm takes 2 off the bill, so it is exhausted before any planet.
    @Test
    void paysWithAiDevelopmentAlgorithmWithTwoUnitUpgrades() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        test.nekro.addTech("aida");
        test.nekro.addTech("cv2");
        test.nekro.addTech("dd2");
        test.game.setStoredValue("producedUnitCostFornekro", "3");
        AiPrompt payment = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW + 10,
                List.of("spend_mordaiii_res", "exhaustTech_aida", "deleteButtons_tacticalAction"),
                List.of("Mordai II", "Exhaust AI Development Algorithm (2r)", "Done Exhausting Planets"));

        assertThat(pressedId(TacticalRules.continueAction(test.context(payment)).orElseThrow()))
                .isEqualTo("exhaustTech_aida");

        test.nekro.exhaustTech("aida");
        test.nekro.addSpentThing("aida_");
        assertThat(pressedId(TacticalRules.continueAction(test.context(payment)).orElseThrow()))
                .isEqualTo("spend_mordaiii_res");
    }

    // The system picker groups tiles by ring; ring 5 and beyond are split in two halves before the full ring.
    @Test
    void picksTheRingButtonForAPosition() {
        assertThat(TacticalRules.ringButtons("305")).containsExactly("ring_3");
        assertThat(TacticalRules.ringButtons("101")).containsExactly("ring_1");
        assertThat(TacticalRules.ringButtons("503")).containsExactly("ring_5_right", "ring_5");
        assertThat(TacticalRules.ringButtons("515")).containsExactly("ring_5_right", "ring_5");
        assertThat(TacticalRules.ringButtons("516")).containsExactly("ring_5_left", "ring_5");
        assertThat(TacticalRules.ringButtons("530")).containsExactly("ring_5_left", "ring_5");
        assertThat(TacticalRules.ringButtons("tl")).containsExactly("ring_corners");
        assertThat(TacticalRules.ringButtons("br")).containsExactly("ring_corners");
    }

    // Unit move buttons carry an optional state segment (damaged, galvanized) between the unit and its holder.
    @Test
    void matchesUnitMoveButtonsWithOrWithoutAUnitState() {
        assertThat(TacticalRules.matchesTail("mordaiii_black", "mordaiii_black"))
                .isTrue();
        assertThat(TacticalRules.matchesTail("glv_mordaiii_black", "mordaiii_black"))
                .isTrue();
        assertThat(TacticalRules.matchesTail("dmg_black", "black")).isTrue();
        assertThat(TacticalRules.matchesTail("dmg_glv_black", "black")).isTrue();
        assertThat(TacticalRules.matchesTail("black", "black")).isTrue();
    }

    // A planet name in front of the colour means a unit on that planet, not one in space.
    @Test
    void rejectsUnitMoveButtonsForAnotherHolder() {
        assertThat(TacticalRules.matchesTail("mordaiii_black", "black")).isFalse();
        assertThat(TacticalRules.matchesTail("foo_mordaiii_black", "mordaiii_black"))
                .isFalse();
        assertThat(TacticalRules.matchesTail("glv_lodor_black", "mordaiii_black"))
                .isFalse();
        assertThat(TacticalRules.matchesTail("darkblack", "black")).isFalse();
    }

    @Test
    void movesAGalvanizedInfantryWhenThatIsTheOnlyButton() {
        activate();
        AiPrompt units = prompt(
                "units",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_unitTacticalMove_301_1_gf_lodor_black",
                "FFCC_nekro_unitTacticalMove_301_1_gf_glv_mordaiii_black",
                "FFCC_nekro_doneWithOneSystem_301");
        stage("301-space", UnitType.Carrier, 1);

        assertThat(pressedId(TacticalRules.continueAction(test.context(units)).orElseThrow()))
                .isEqualTo("FFCC_nekro_unitTacticalMove_301_1_gf_glv_mordaiii_black");
    }

    // Production screens without a "2 infantry" button only offer single infantry.
    @Test
    void buildsSingleInfantryWhenThePairButtonIsMissing() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        test.memory.put(
                TacticalRules.BUILD_KEY + test.context().turnKey(),
                new ProductionPlanner.BuildPlan(List.of(new ProductionPlanner.BuildOrder(
                                "2gf", "mordaiii", UnitType.Infantry, 2, 1.0, 1.0, false)))
                        .encode());
        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "FFCC_nekro_place_carrier_301",
                        "FFCC_nekro_place_infantry_mordaiii",
                        "FFCC_nekro_deleteButtons_tacticalAction_301"),
                List.of("Produce Carrier", "Produce Infantry", "Done Producing Units"));

        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("FFCC_nekro_place_infantry_mordaiii");
    }

    // Systems with three or more planets offer no "2 fighters" button, so fighters are built one at a time.
    @Test
    void buildsSingleFightersWhenThePairButtonIsMissing() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        test.memory.put(
                TacticalRules.BUILD_KEY + test.context().turnKey(),
                new ProductionPlanner.BuildPlan(List.of(new ProductionPlanner.BuildOrder(
                                "2ff", AiTestGame.HOME, UnitType.Fighter, 2, 1.0, 0.2, false)))
                        .encode());
        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                List.of("FFCC_nekro_place_fighter_301", "FFCC_nekro_deleteButtons_tacticalAction_301"),
                List.of("Produce 1 Fighter", "Done Producing Units"));

        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("FFCC_nekro_place_fighter_301");

        test.nekro.produceUnit("ff_301_space");
        test.nekro.produceUnit("ff_301_space");
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons_tacticalAction_301");
    }

    // Only pairs of infantry or fighters have a single-unit fallback; a missing ship button means the AI is done
    // building.
    @Test
    void finishesBuildingWhenAPlannedShipButtonIsMissing() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        test.memory.put(
                TacticalRules.BUILD_KEY + test.context().turnKey(),
                new ProductionPlanner.BuildPlan(List.of(new ProductionPlanner.BuildOrder(
                                "dreadnought", "301", UnitType.Dreadnought, 1, 4.0, 2.0, true)))
                        .encode());
        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                List.of("FFCC_nekro_place_infantry_mordaiii", "FFCC_nekro_deleteButtons_tacticalAction_301"),
                List.of("Produce Infantry", "Done Producing Units"));

        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons_tacticalAction_301");
    }

    // With Yin Spinner the production message also offers 2 free infantry; it takes them once before it is done.
    @Test
    void spinsTwoInfantryWithYinSpinnerBeforeFinishingTheBuild() {
        test.nekro.addTech("yso");
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                List.of("FFCC_nekro_deleteButtons_tacticalAction_301", "startYinSpinner"),
                List.of("Done Producing Units", "Yin Spin 2 Duders"));

        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("startYinSpinner");
        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons_tacticalAction_301");
    }

    // Ground forces of two players on one planet mean a ground combat. If no combat thread shows up within the
    // grace period, the AI stops guessing and asks for help instead of concluding the action.
    @Test
    void waitsForTheGroundCombatThreadThenGivesUp() {
        activateWithGroundCombat();
        AiPrompt conclude = prompt("conclude", PromptSource.PUBLIC, NOW, "FFCC_nekro_doneWithTacticalAction");

        AiDecision first = TacticalRules.continueAction(test.context(conclude)).orElseThrow();
        assertThat(first).isInstanceOf(AiDecision.Wait.class);
        long graceEnds = ((AiDecision.Wait) first).untilMillis();
        assertThat(graceEnds).isGreaterThan(NOW);

        assertThat(TacticalRules.continueAction(test.contextAt(graceEnds - 1, conclude))
                        .orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);
        AiDecision later = TacticalRules.continueAction(test.contextAt(graceEnds, conclude))
                .orElseThrow();
        assertThat(later).isInstanceOf(AiDecision.Unsure.class);
        assertThat(((AiDecision.Unsure) later).prompt()).isEqualTo(conclude);
    }

    @Test
    void keepsWaitingWhileTheCombatThreadIsOpen() {
        activateWithGroundCombat();
        AiPrompt conclude = prompt("conclude", PromptSource.PUBLIC, NOW, "FFCC_nekro_doneWithTacticalAction");
        AiPrompt thread = new AiPrompt("combat", "combat-message", PromptSource.COMBAT_THREAD, "", List.of(), NOW);

        AiDecision decision = TacticalRules.continueAction(test.contextAt(NOW + 3_600_000L, conclude, thread))
                .orElseThrow();

        assertThat(decision).isInstanceOf(AiDecision.Wait.class);
    }

    private void activateWithGroundCombat() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + target + " (Lodor).");
        test.game.setActiveSystem(target);
        Tile tile = test.game.getTileByPosition(target);
        test.units(tile, "lodor", test.nekro, UnitType.Infantry, 1);
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 1);
    }

    private void activate() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + target + " (Lodor).");
        test.game.setActiveSystem(target);
        TacticalPlan plan = new TacticalPlan(
                Kind.EXPAND,
                target,
                List.of(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 1)),
                Map.of("lodor", 1),
                3.4);
        test.memory.put(TacticalRules.PLAN_KEY + test.context().turnKey(), plan.encode());
    }

    private void stage(String key, UnitType type, int count) {
        Map<UnitKey, List<Integer>> units =
                test.game.getTacticalActionDisplacement().computeIfAbsent(key, ignored -> new HashMap<>());
        List<Integer> states = new ArrayList<>(List.of(count, 0, 0, 0));
        units.put(Units.getUnitKey(type, "black"), states);
    }
}
