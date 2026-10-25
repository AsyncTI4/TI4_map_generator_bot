package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

// War Machine ("When 1 or more of your units use PRODUCTION": +4 PRODUCTION and 1 off the cost) is played the next
// time the seat builds and can use the extra production. The build plan with the card waits for the card to resolve;
// a Sabotaged card falls back to the plan without it.
class WarMachineBuildTest extends BaseTi4Test {

    private static final int HAND_INDEX = 77;
    private static final long SETTLED = NOW + 11_000L;
    private static final String WAR_MACHINE = "War Machine";

    private AiTestGame test;
    private Tile home;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.nekro.setFleetCC(5);
        test.aiIsActive("action");
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1),
                        GameMessageType.ACTION_CARD,
                        new LinkedHashSet<>(List.of("nekro", "sol")),
                        0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    // Mordai II (4 resources) gives the home dock production 6; with 10 trade goods the plan fills all of it.
    private void dockShortOfProduction() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.nekro.setTg(10);
    }

    private BuildPlan basePlan() {
        return ProductionPlanner.plan(test.game, test.nekro, home);
    }

    private Optional<BuildPlan> warMachinePlan() {
        return ProductionPlanner.warMachinePlan(
                test.game, test.nekro, home, basePlan(), new DefenseOutlook(test.game, test.nekro));
    }

    @Test
    void aDockThatRunsOutOfProductionUsesTheExtraProduction() {
        dockShortOfProduction();
        int production = Helper.getProductionValue(test.nekro, test.game, home, false);

        BuildPlan boosted = warMachinePlan().orElseThrow();

        assertThat(basePlan().units()).isEqualTo(production);
        assertThat(boosted.units()).isGreaterThan(production);
        assertThat(boosted.value()).isGreaterThan(basePlan().value());
    }

    // One resource buys two infantry, the card's discount two more: four units never reach the dock's production 6.
    @Test
    void aBuildShortOfResourcesCannotUseTheExtraProduction() {
        test.nekro.exhaustPlanet("mordaiii");
        test.nekro.setTg(1);

        assertThat(warMachinePlan()).isEmpty();
    }

    @Test
    void theBuildPressRemembersThePlanWithoutTheCard() {
        dockShortOfProduction();
        test.nekro.setActionCard("war_machine1", HAND_INDEX);
        AiPrompt build = prompt("build", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalActionBuild_301");

        assertThat(pressedId(TacticalRules.continueAction(test.context(build)).orElseThrow()))
                .isEqualTo("FFCC_nekro_tacticalActionBuild_301");
        BuildPlan planned = storedPlan(TacticalRules.BUILD_KEY).orElseThrow();
        BuildPlan without = storedPlan(TacticalRules.WAR_MACHINE_KEY).orElseThrow();
        assertThat(planned.units()).isGreaterThan(without.units());
    }

    @Test
    void noPlanWithoutTheCardIsKeptWhenTheSeatHoldsNoWarMachine() {
        dockShortOfProduction();
        AiPrompt build = prompt("build", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalActionBuild_301");

        TacticalRules.continueAction(test.context(build));

        assertThat(storedPlan(TacticalRules.WAR_MACHINE_KEY)).isEmpty();
    }

    private Optional<BuildPlan> storedPlan(String prefix) {
        return test.memory.get(prefix + test.context().turnKey()).flatMap(BuildPlan::decode);
    }

    private static final BuildPlan WITHOUT =
            new BuildPlan(List.of(new BuildOrder("2gf", "mordaiii", UnitType.Infantry, 2, 1.0, 1.0, false, false)));
    private static final BuildPlan WITH = new BuildPlan(List.of(
            new BuildOrder("carrier", AiTestGame.HOME, UnitType.Carrier, 1, 3.0, 2.5, false, false),
            new BuildOrder("2gf", "mordaiii", UnitType.Infantry, 2, 1.0, 1.0, false, false)));

    private void plannedWithTheCard() {
        test.nekro.setActionCard("war_machine1", HAND_INDEX);
        test.memory.put(TacticalRules.BUILD_KEY + test.context().turnKey(), WITH.encode());
        test.memory.put(TacticalRules.WAR_MACHINE_KEY + test.context().turnKey(), WITHOUT.encode());
    }

    private static AiPrompt production() {
        return prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "FFCC_nekro_place_carrier_301",
                        "FFCC_nekro_place_2gf_mordaiii",
                        "FFCC_nekro_deleteButtons_tacticalAction_301"),
                List.of("Produce Carrier", "Produce 2 Infantry", "Done Producing Units"));
    }

    private static AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private static AiPrompt announcement() {
        return prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_" + WAR_MACHINE,
                "no_sabotage",
                "sabotage_ac_" + WAR_MACHINE + "_nekro");
    }

    // What the engine does when the card leaves the hand: the play is logged and +4 PRODUCTION is marked as spent.
    private void cardPlayed() {
        test.nekro.getActionCards().clear();
        test.game.getGameStats().recordAcPlay(WAR_MACHINE, test.nekro);
        test.nekro.addSpentThing("warmachine");
    }

    private void throughTheSabotageWindow() {
        ActionCardRules.continuePlay(test.contextAt(NOW + 1_000L, announcement()));
        ActionCardRules.continuePlay(test.contextAt(SETTLED, announcement()));
    }

    @Test
    void theCardIsPlayedOnTheProductionScreenBeforeAnyUnitIsPlaced() {
        plannedWithTheCard();

        assertThat(pressedId(TacticalRules.continueAction(test.context(production(), hand()))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void noUnitIsPlacedWhileTheCardIsInItsSabotageWindow() {
        plannedWithTheCard();
        TacticalRules.continueAction(test.context(production(), hand()));
        cardPlayed();

        assertThat(TacticalRules.continueAction(test.contextAt(NOW + 1_000L, production())))
                .containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void aResolvedCardBuildsThePlanWithTheExtraProduction() {
        plannedWithTheCard();
        TacticalRules.continueAction(test.context(production(), hand()));
        cardPlayed();
        throughTheSabotageWindow();

        assertThat(pressedId(TacticalRules.continueAction(test.contextAt(SETTLED, production()))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_place_carrier_301");
    }

    // The engine takes the +4 PRODUCTION back when the card is cancelled, so the bot must not over-build.
    @Test
    void aSabotagedCardFallsBackToThePlanWithoutIt() {
        plannedWithTheCard();
        TacticalRules.continueAction(test.context(production(), hand()));
        cardPlayed();
        test.game.getGameStats().markLatestPlayCanceled(WAR_MACHINE);
        test.nekro.removeSpentThing("warmachine");
        throughTheSabotageWindow();

        assertThat(pressedId(TacticalRules.continueAction(test.contextAt(SETTLED, production()))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_place_2gf_mordaiii");
        assertThat(storedPlan(TacticalRules.BUILD_KEY)).contains(WITHOUT);
    }

    // The play button is gone (the hand could not be brought back into view), so the seat builds without the card.
    @Test
    void aCardThatCannotBePlayedFallsBackToThePlanWithoutIt() {
        plannedWithTheCard();

        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(production())).orElseThrow()))
                .isEqualTo("FFCC_nekro_place_2gf_mordaiii");
    }

    @Test
    void theCardsDiscountIsCountedWhenPaying() {
        test.nekro.addSpentThing("warmachine");
        test.game.setStoredValue("producedUnitCostFornekro", "1");
        AiPrompt payment = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW + 10,
                List.of("spend_mordaiii_res", "reduceTG_1_res", "deleteButtons_tacticalAction"),
                List.of("Mordai II", "Spend 1 TG", "Done Exhausting Planets"));

        assertThat(pressedId(TacticalRules.continueAction(test.context(payment)).orElseThrow()))
                .isEqualTo("deleteButtons_tacticalAction");
    }
}
