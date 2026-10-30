package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.trade.RaisableTradeGoods;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TechnologyTiming {

    public static final int LAST_DEAL_ROUND = 2;
    private static final int LAST_TIMED_ROUND = 2;
    private static final int TRADE_PRIMARY_TRADE_GOODS = 3;
    private static final double EARLY_PLAY_SHARE = 0.2;
    private static final double HOLD_DELAY_COST = 0.5;
    private static final int HOLD_PAYMENT = 1;
    private static final int LONG_HOLD_PAYMENT = 2;
    private static final int PASSES_ENDING_A_HOLD = 2;
    private static final String HELD = "technologyHeld|";
    private static final char GRANT_SEPARATOR = '|';

    static boolean playsNow(PlanInputs in) {
        if (in.round() > LAST_TIMED_ROUND) return false;
        double delay = earlyPlayCost(in);
        return delay == 0 || denialGain(in.game(), in.seat()) > delay;
    }

    public static double earlyCost(Game game, Player holder) {
        PlanInputs in = PlanInputs.of(game, holder, PlanMoment.ACTION);
        return Math.max(0, earlyPlayCost(in) - denialGain(game, holder));
    }

    public static double holdCost(Game game, Player holder, Player payer) {
        double cost = holdCost(game, holder);
        return awaitsTradeWash(game, payer) ? cost + HOLD_DELAY_COST : cost;
    }

    public static int holdPayment(Game game, Player payer) {
        return awaitsTradeWash(game, payer) ? LONG_HOLD_PAYMENT : HOLD_PAYMENT;
    }

    public static boolean awaitsTradeWash(Game game, Player payer) {
        int washed = RaisableTradeGoods.fromRivalTrade(game, payer) + RaisableTradeGoods.byWashing(game, payer);
        return washed > 0
                && payer.getStrategicCC() > 0
                && affords(game, payer, StrategyCardRules.FOLLOW_TECH_COST - washed);
    }

    public static double holdCost(Game game, Player holder) {
        if (holder.getTacticalCC() == 0 || CardTiming.researchesGravityDriveForTheOpening(game, holder)) {
            return Double.POSITIVE_INFINITY;
        }
        PlanInputs in = PlanInputs.of(game, holder, PlanMoment.ACTION);
        double delay = earlyPlayCost(in);
        double denial = denialGain(game, holder);
        boolean playsEarly = delay == 0 || denial > delay;
        return playsEarly ? Math.max(HOLD_DELAY_COST, denial - delay) : 0;
    }

    public static double denialGain(Game game, Player holder) {
        List<Player> opponents =
                game.getRealPlayers().stream().filter(other -> other != holder).toList();
        if (opponents.isEmpty()) return 0;
        double denied = opponents.stream()
                .filter(other -> deniable(game, other))
                .mapToDouble(other -> followSurplus(game, other))
                .sum();
        return CardValueScale.DENIAL_SHARE * denied / opponents.size();
    }

    public static boolean followsNow(Game game, Player seat) {
        return seat.getStrategicCC() > 0 && affords(game, seat, StrategyCardRules.FOLLOW_TECH_COST);
    }

    public static boolean deniable(Game game, Player seat) {
        return !followsNow(game, seat) && followsLaterAfterPaying(game, seat, 0) && wantsToResearch(game, seat);
    }

    public static boolean followsLaterAfterPaying(Game game, Player seat, int tradeGoods) {
        boolean token = seat.getStrategicCC() > 0 || holdsUnplayed(game, seat, StrategyCard.LEADERSHIP);
        int cost = StrategyCardRules.FOLLOW_TECH_COST + tradeGoods - expectedIncome(game, seat);
        return token && affords(game, seat, cost);
    }

    public static double followSurplus(Game game, Player seat) {
        if (StrategyCardRules.cannotResearch(seat)) return 0;
        return ResearchPolicy.bestResearchable(game, seat)
                .map(alias -> ResearchPolicy.TECH_TEMPO * ResearchPolicy.researchValue(game, seat, alias)
                        - StrategyCardRules.FOLLOW_TECH_COST
                        - CardValueScale.STRATEGY_TOKEN)
                .map(surplus -> Math.max(0, surplus))
                .orElse(0.0);
    }

    public static boolean heldBack(Game game, Player holder, AiMemory memory) {
        Optional<String[]> grant = memory.get(heldKey(game)).map(value -> StringUtils.split(value, GRANT_SEPARATOR));
        if (grant.isEmpty() || grant.get().length != 2 || !StringUtils.isNumeric(grant.get()[1])) return false;
        Player payer = game.getPlayerFromColorOrFaction(grant.get()[0]);
        if (payer == null || CardTiming.researchesGravityDriveForTheOpening(game, holder)) return false;
        if (payer.isPassed() || followsNow(game, payer) || roundWindingDown(game)) return false;
        return !nextTurnOver(game, payer, Integer.parseInt(grant.get()[1])) || awaitsTradeWash(game, payer);
    }

    private static boolean roundWindingDown(Game game) {
        return game.getRealPlayers().stream().filter(Player::isPassed).count() >= PASSES_ENDING_A_HOLD;
    }

    public static void grantHold(Game game, AiMemory memory, Player payer) {
        memory.put(heldKey(game), payer.getFaction() + GRANT_SEPARATOR + payer.getInRoundTurnCount());
    }

    private static boolean nextTurnOver(Game game, Player payer, int turnsWhenGranted) {
        return payer.getInRoundTurnCount() > turnsWhenGranted
                && !payer.getUserID().equals(game.getActivePlayerID());
    }

    public static String heldKey(Game game) {
        return HELD + game.getRound();
    }

    public static boolean holdsUnplayed(Game game, Player player, StrategyCard card) {
        return player.getSCs().stream()
                .anyMatch(initiative ->
                        !game.getPlayedSCs().contains(initiative) && StrategyCard.of(game, initiative) == card);
    }

    static double earlyPlayCost(PlanInputs in) {
        return EARLY_PLAY_SHARE
                * in.ranked().stream()
                        .filter(plan -> plan.kind() == TacticalPlan.Kind.EXPAND)
                        .mapToDouble(TacticalPlan::score)
                        .max()
                        .orElse(0);
    }

    private static boolean wantsToResearch(Game game, Player seat) {
        return !StrategyCardRules.cannotResearch(seat)
                && StrategyCardRules.worthResearching(game, seat, ResearchPolicy.WORTH_FOLLOWING_FOR);
    }

    private static int expectedIncome(Game game, Player seat) {
        int trade = holdsUnplayed(game, seat, StrategyCard.TRADE) ? TRADE_PRIMARY_TRADE_GOODS : 0;
        return trade + Math.max(seat.getCommodities(), RaisableTradeGoods.fromRivalTrade(game, seat));
    }

    private static boolean affords(Game game, Player seat, int resources) {
        return ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(Math.max(0, resources)))
                .isPresent();
    }
}
