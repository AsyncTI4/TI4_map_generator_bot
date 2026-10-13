package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class OpeningHold {

    private static final int LAST_OPENING_ROUND = 1;
    private static final int FIRST_TURN = 1;

    public static boolean awaitsWarfareBuild(Game game, Player seat) {
        if (game.getRound() > LAST_OPENING_ROUND || seat.getInRoundTurnCount() > FIRST_TURN) return false;
        if (seat.getStrategicCC() <= 0 || holdsWarfare(game, seat)) return false;
        if (!rivalWarfareStillToFollow(game, seat)) return false;
        if (!StrategyCardRules.hasSpareStrategyToken(game, seat, StrategyCard.WARFARE)) return false;
        if (!StrategyCardRules.wantsToFollow(game, seat, StrategyCard.WARFARE)) return false;
        return OpeningOutlook.holdsCarriersForHomeBuild(game, seat);
    }

    private static boolean holdsWarfare(Game game, Player seat) {
        return seat.getSCs().stream().anyMatch(initiative -> StrategyCard.of(game, initiative) == StrategyCard.WARFARE);
    }

    private static boolean rivalWarfareStillToFollow(Game game, Player seat) {
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (int initiative : other.getSCs()) {
                if (StrategyCard.of(game, initiative) != StrategyCard.WARFARE) continue;
                if (CardVariant.of(game, initiative) != CardVariant.THUNDERS_EDGE) continue;
                if (!game.getPlayedSCs().contains(initiative) || !seat.hasFollowedSC(initiative)) return true;
            }
        }
        return false;
    }
}
