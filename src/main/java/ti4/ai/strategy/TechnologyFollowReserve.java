package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.SpendCost;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TechnologyFollowReserve {

    private static final String ACTION_PHASE = "action";

    public static SpendCost of(Game game, Player seat) {
        return pending(game, seat) ? SpendCost.resources(StrategyCardRules.FOLLOW_TECH_COST) : SpendCost.NONE;
    }

    private static boolean pending(Game game, Player seat) {
        return ACTION_PHASE.equalsIgnoreCase(game.getPhaseOfGame())
                && rivalHoldsUnplayedTechnology(game, seat)
                && !StrategyCardRules.cannotResearch(seat)
                && !OpeningHold.awaitsGravityDrive(game, seat)
                && seat.getStrategicCC() > 0
                && StrategyCardRules.wantsToFollow(game, seat, StrategyCard.TECHNOLOGY);
    }

    private static boolean rivalHoldsUnplayedTechnology(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .anyMatch(other -> TechnologyTiming.holdsUnplayed(game, other, StrategyCard.TECHNOLOGY));
    }
}
