package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class OpeningHold {

    private static final int LAST_OPENING_ROUND = 1;
    private static final int FIRST_TURN = 1;
    private static final String GRAVITY_DRIVE = "gd";

    public static boolean awaitsWarfareBuild(Game game, Player seat) {
        if (game.getRound() > LAST_OPENING_ROUND || seat.getInRoundTurnCount() > FIRST_TURN) return false;
        if (seat.getStrategicCC() <= 0 || holdsWarfare(game, seat)) return false;
        if (!rivalWarfareStillToFollow(game, seat)) return false;
        if (!StrategyCardRules.hasSpareStrategyToken(game, seat, StrategyCard.WARFARE)) return false;
        if (!StrategyCardRules.wantsToFollow(game, seat, StrategyCard.WARFARE)) return false;
        return OpeningOutlook.holdsCarriersForHomeBuild(game, seat);
    }

    public static boolean awaitsGravityDrive(Game game, Player seat) {
        if (game.getRound() > LAST_OPENING_ROUND || seat.hasTech(GRAVITY_DRIVE)) return false;
        Tile home = seat.getHomeSystemTile();
        if (home == null || home.hasPlayerCC(seat)) return false;
        return OpeningNeeds.wantsTechnologySoon(game, seat);
    }

    public static SpendCost gravityDriveReserve(Game game, Player seat) {
        if (!awaitsGravityDrive(game, seat)) return SpendCost.NONE;
        int fromTradeGoodsToCome = Math.max(
                0,
                OpeningOutlook.expectedTradeGoods(game, seat) - ScoringReserve.expectedTradeGoodsCounted(game, seat));
        return SpendCost.resources(Math.max(0, StrategyCardRules.FOLLOW_TECH_COST - fromTradeGoodsToCome));
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
