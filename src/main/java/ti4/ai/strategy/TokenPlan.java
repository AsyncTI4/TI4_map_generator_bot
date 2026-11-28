package ti4.ai.strategy;

import java.util.HashSet;
import java.util.Set;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;

public record TokenPlan(int strategy, int tactic) {

    private static final int MAX_PLANNED_ACTIVATIONS = 4;

    public static TokenPlan of(Game game, Player seat) {
        try (Thinking.Span ignored = Thinking.enter("strategy.tokenPlan")) {
            return new TokenPlan(
                    expectedFollows(game, seat) + strategizeTokens(game, seat), expectedActivations(game, seat));
        }
    }

    static int expectedFollows(Game game, Player seat) {
        int follows = 0;
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (int initiative : other.getSCs()) {
                if (initiative <= 0 || seat.hasFollowedSC(initiative)) continue;
                if (StrategyCardRules.wantsToFollow(game, seat, StrategyCard.of(game, initiative))) follows++;
            }
        }
        return follows;
    }

    static int strategizeTokens(Game game, Player seat) {
        return ActionCardRules.strategizeSecondaryWorthAToken(game, seat) ? 1 : 0;
    }

    static int expectedActivations(Game game, Player seat) {
        Set<String> targets = new HashSet<>();
        for (TacticalPlan plan : TacticalPlanner.ranked(game, seat)) {
            targets.add(plan.target());
            if (targets.size() >= MAX_PLANNED_ACTIVATIONS) break;
        }
        return targets.size() + ScoringReserve.of(game, seat).tokens();
    }
}
