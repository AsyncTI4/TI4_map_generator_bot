package ti4.ai.legendary;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TempestaRules {

    private static final String KEY = "tempesta|";

    public static Optional<AiDecision> boost(AiTurnContext context, AiPrompt movement, Optional<TacticalPlan> plan) {
        Player seat = context.seat();
        String key = KEY + TacticalRules.actionKey(context);
        if (plan.isEmpty()
                || context.memory().has(key)
                || !LegendaryPlanets.abilityReady(seat, LegendaryPlanets.TEMPESTA)
                || !needsBoost(context.game(), seat, plan.get())) {
            return Optional.empty();
        }
        return movement.firstEnabled(button -> button.isUnowned()
                        && LegendaryPlanets.button(LegendaryPlanets.TEMPESTA).equals(button.handlerId()))
                .filter(button -> !context.alreadyPressed(movement, button))
                .map(button -> {
                    context.memory().put(key, "used");
                    return AiDecision.press(movement, button, "exhaust the Ionian Fuel Refinery for +1 movement");
                });
    }

    static boolean needsBoost(Game game, Player seat, TacticalPlan plan) {
        for (UnitMove move : plan.moves()) {
            if (move.origin().equals(plan.target())) continue;
            int unboosted = BoardView.moveValueWithGravityDrive(seat, move.type());
            if (!MovementGraph.reach(game, seat, move.origin(), unboosted).containsKey(plan.target())) return true;
        }
        return false;
    }
}
