package ti4.ai.tech;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.testUtils.BaseTi4Test;

// Production Biomes mid-round with a strategy token to spare and a tactical plan worth a little less than the 4 trade
// goods plus a stall. Under STALL_TIMING the stall only counts instead of a tactical action when the trade goods
// unlock a point; otherwise Production Biomes waits for the beforePassing slot.
class ProductionBiomesStallTimingTest extends BaseTi4Test {

    private AiTestGame test;
    private AiPrompt start;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        test.nekro.addTech("pm");
        test.nekro.setStrategicCC(2);
        start = prompt(
                "start",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_componentAction",
                "FFCC_nekro_passForRound");
    }

    private AiTurnContext contextWithPlanBetweenBiomesAndStall() {
        AiTurnContext context = test.context(start);
        double biomes = TechRules.productionBiomesValue(test.game, test.nekro);
        double stall = Stall.actionValue(test.game, test.nekro);
        assertThat(stall).isPositive();
        TacticalRules.remember(
                context,
                new TacticalPlan(TacticalPlan.Kind.POSITION, AiTestGame.HOME, List.of(), Map.of(), biomes + stall / 2));
        return context;
    }

    // One trade good short of Negotiate Trade Routes: the 4 trade goods unlock a point.
    private void oneTradeGoodShortOfTradeRoutes() {
        test.nekro.setTg(1);
        test.game.getRevealedPublicObjectives().put("trade_routes", 1);
    }

    @Test
    void takesTheTacticalActionBeforeProductionBiomes() {
        AiTurnContext context = contextWithPlanBetweenBiomesAndStall();

        assertThat(TechRules.insteadOfTacticalAction(context, List.of(start))).isEmpty();
        assertThat(pressedId(TechRules.beforePassing(context, List.of(start)).orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }

    @Test
    void theDisabledExperimentStillTakesProductionBiomesFirst() {
        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.STALL_TIMING));
        AiTurnContext context = contextWithPlanBetweenBiomesAndStall();

        assertThat(pressedId(TechRules.insteadOfTacticalAction(context, List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }

    // The tactical plan is still placed between the value and the value plus a stall, so only the kept stall credit
    // puts Production Biomes ahead of it.
    @Test
    void keepsTheStallCreditWhenTheTradeGoodsUnlockAPoint() {
        oneTradeGoodShortOfTradeRoutes();
        AiTurnContext context = contextWithPlanBetweenBiomesAndStall();

        assertThat(pressedId(TechRules.insteadOfTacticalAction(context, List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }
}
