package ti4.ai.explore;

import lombok.experimental.UtilityClass;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.nekro.CommandTokenPolicy.Pool;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class ExploreGains {

    private static final double FLEET_SUPPLY_BONUS = 1.0;
    private static final double EXTRA_FOLLOW_BONUS = 1.0;

    static double commandToken(Game game, Player seat) {
        if (StrategyCardRules.reinforcements(game, seat) <= 0) return 0;
        return ExploreValues.COMMAND_TOKEN + visibleUse(game, seat);
    }

    static double tradeGood(Game game, Player seat) {
        return ExploreValues.TRADE_GOOD + unlockedPoints(SpendUnlock.pointDelta(game, seat, 1));
    }

    private static double visibleUse(Game game, Player seat) {
        double unlock = unlockedPoints(SpendUnlock.pointsUnlockedByTokens(game, seat, 1));
        String pool = CommandTokenPolicy.poolToGrow(game, seat);
        if (Pool.FLEET.grow().equals(pool)) return unlock + FLEET_SUPPLY_BONUS;
        if (Pool.STRATEGY.grow().equals(pool) && followsWantedBeyondTokens(game, seat)) {
            return unlock + EXTRA_FOLLOW_BONUS;
        }
        return unlock + tacticalGainOverBaseline(game, seat);
    }

    private static boolean followsWantedBeyondTokens(Game game, Player seat) {
        return StrategyCardRules.strategyTokensWantedForFollows(game, seat) > seat.getStrategicCC();
    }

    private static double tacticalGainOverBaseline(Game game, Player seat) {
        return Math.max(0, CommandTokenValue.tactic(game, seat, 1) - ExploreValues.COMMAND_TOKEN);
    }

    private static double unlockedPoints(int points) {
        return points * CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT;
    }
}
