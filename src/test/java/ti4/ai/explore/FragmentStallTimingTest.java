package ti4.ai.explore;

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
import ti4.ai.eval.RelicValue;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.testUtils.BaseTi4Test;

// A full fragment set mid-round, with a tactical plan worth a little less than the relic share plus a stall. The
// stall is only worth something at the end of the round, so with STALL_TIMING the tactical action goes first and
// the fragments wait for the beforePassing slot.
class FragmentStallTimingTest extends BaseTi4Test {

    private AiTestGame test;
    private AiPrompt start;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        for (String id : List.of("crf1", "crf2", "crf3")) test.nekro.addFragment(id);
        start = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction");
    }

    private AiTurnContext contextWithPlanBetweenRelicShareAndStall() {
        AiTurnContext context = test.context(start);
        double share = RelicValue.draw(test.game, test.nekro) * FragmentRules.EARLY_USE_SHARE;
        double stall = Stall.value(test.game, test.nekro);
        assertThat(stall).isPositive();
        TacticalRules.remember(
                context,
                new TacticalPlan(TacticalPlan.Kind.POSITION, AiTestGame.HOME, List.of(), Map.of(), share + stall / 2));
        return context;
    }

    @Test
    void takesTheTacticalActionBeforeTheFragments() {
        AiTurnContext context = contextWithPlanBetweenRelicShareAndStall();

        assertThat(RelicActionRules.insteadOfTacticalAction(context, List.of(start)))
                .isEmpty();
        assertThat(pressedId(
                        RelicActionRules.beforePassing(context, List.of(start)).orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }

    @Test
    void theDisabledExperimentStillPurgesTheFragmentsFirst() {
        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.STALL_TIMING));
        AiTurnContext context = contextWithPlanBetweenRelicShareAndStall();

        assertThat(pressedId(RelicActionRules.insteadOfTacticalAction(context, List.of(start))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }
}
