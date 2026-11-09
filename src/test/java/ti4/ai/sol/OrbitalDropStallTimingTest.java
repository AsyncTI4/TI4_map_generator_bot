package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Sol with Jord and Lodor and three strategy tokens, Nekro still acting. The Orbital Drop plan's net includes the stall
// credit; the tactical plan sits between the net without it and the net with it. Under STALL_TIMING the drop waits
// for the beforePassing slot, which still counts the stall.
class OrbitalDropStallTimingTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private String lodorPosition;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(4);
        Tile home = test.place("01", AiTestGame.HOME);
        sol.addPlanet("jord");
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        test.units(home, "jord", sol, UnitType.Infantry, 2);
        lodorPosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", lodorPosition);
        sol.addPlanet("lodor");
        sol.setStrategicCC(3);
        sol.setTacticalCC(3);
        sol.setFleetCC(5);
        test.isActive(sol, "action");
    }

    private AiTurnContext context() {
        AiPrompt menu = prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sol_tacticalAction",
                "FFCC_sol_componentAction",
                "FFCC_sol_passForRound");
        return test.contextFor(sol, Set.of(), NOW, menu);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiTurnContext contextWithPlanBetweenDropAndStall() {
        AiTurnContext context = context();
        double stall = Stall.actionValue(test.game, sol);
        assertThat(stall).isPositive();
        TacticalPlan probe = new TacticalPlan(Kind.POSITION, lodorPosition, List.of(), Map.of(), 0);
        double net =
                OrbitalDropRules.plan(context, Optional.of(probe)).orElseThrow().net();
        TacticalRules.remember(
                context, new TacticalPlan(Kind.POSITION, lodorPosition, List.of(), Map.of(), net - stall / 2));
        return context;
    }

    @Test
    void takesTheTacticalActionBeforeTheDrop() {
        AiTurnContext context = contextWithPlanBetweenDropAndStall();

        assertThat(OrbitalDropRules.insteadOfTacticalAction(context, Prompts.thisTurn(context)))
                .isEmpty();
        assertThat(pressed(OrbitalDropRules.beforePassing(context, Prompts.thisTurn(context))))
                .isEqualTo("FFCC_sol_componentAction");
    }

    @Test
    void theDisabledExperimentStillDropsFirst() {
        AiExperiments.disable(sol, EnumSet.of(Experiment.STALL_TIMING));
        AiTurnContext context = contextWithPlanBetweenDropAndStall();

        assertThat(pressed(OrbitalDropRules.insteadOfTacticalAction(context, Prompts.thisTurn(context))))
                .isEqualTo("FFCC_sol_componentAction");
    }
}
