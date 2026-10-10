package ti4.ai.explore;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.trade.ComponentValues;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class ExploreValues {

    public static final double TRADE_GOOD = 1.0;
    public static final double COMMAND_TOKEN = 2.0;
    public static final double ACTION_CARD = 1.0;
    public static final double MECH = 2.0;
    public static final double MECH_GARRISON = 0.5;
    public static final double INFANTRY_UNIT = 0.7;
    public static final double EXPANSION_SHARE = 0.5;
    public static final double LAST_FORCE_RISK_SHARE = 0.15;
    public static final double UNSPENT_READY_SHARE = 0.3;
    public static final double READY_PLANET_SHARE = 0.9;
    public static final double MERCENARY_OUTFIT = 0.75;
    public static final double FREELANCERS = 1.5;
    public static final int FREELANCERS_TYPICAL_COST = 2;
    public static final double SECRET_OBJECTIVE = 1.5;
    public static final double CROWDED_SECRET_OBJECTIVE = 0.3;
    public static final double ENIGMATIC_DEVICE = 1.0;
    public static final double DEMILITARIZED_ZONE = -1.5;
    private static final int ACTION_CARDS_DRAWN_BY_LOST_CREW = 2;

    public static double tokenValue(Game game, Player seat) {
        return StrategyCardRules.reinforcements(game, seat) > 0 ? COMMAND_TOKEN : 0;
    }

    public static double drawnActionCards(Game game, Player seat, int count) {
        int limit = ButtonHelper.getACLimit(game, seat);
        int room = Math.max(0, limit - seat.getAcCount());
        return Math.min(count, room) * ACTION_CARD;
    }

    public static double actionCardsFromLostCrew(Game game, Player seat) {
        return drawnActionCards(game, seat, ACTION_CARDS_DRAWN_BY_LOST_CREW);
    }

    public static double commodityValue(Game game, Player seat) {
        return ComponentValues.ownCommodity(game, seat);
    }

    public static int commodityRoom(Player seat) {
        return Math.max(0, seat.getCommoditiesTotal() - seat.getCommodities());
    }

    public static double gainedCommodities(Game game, Player seat, int count) {
        return Math.min(count, commodityRoom(seat)) * commodityValue(game, seat);
    }

    public static double convertedCommodities(Game game, Player seat, int count) {
        return Math.min(count, seat.getCommodities()) * (TRADE_GOOD - commodityValue(game, seat));
    }

    public static boolean canPayCommodityOrTradeGood(Player seat) {
        return seat.getCommodities() > 0 || seat.getTg() > 0;
    }

    public static double commodityOrTradeGoodCost(Game game, Player seat) {
        return seat.getCommodities() > 0 ? commodityValue(game, seat) : TRADE_GOOD;
    }

    public static boolean hasRoomForUnit(Game game, Player seat, UnitType type) {
        String asyncId =
                BoardView.model(seat, type).map(model -> model.getAsyncId()).orElse(null);
        if (asyncId == null) return false;
        int cap = seat.getUnitCap(asyncId);
        return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId) < cap;
    }
}
