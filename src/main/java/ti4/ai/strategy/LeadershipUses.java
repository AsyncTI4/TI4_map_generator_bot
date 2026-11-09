package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.endurance.Endurance;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Player;

@UtilityClass
class LeadershipUses {

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        return List.of(
                new Benefit(
                        CardUse.LEADERSHIP_TOKENS, TokenValue.carriedTokens(in, StrategyCardRules.LEADERSHIP_TOKENS)),
                new Benefit(
                        CardUse.LEADERSHIP_EXPEDITION,
                        CardValueScale.EXPEDITION_FIRST_SHARE * ExpeditionUnlock.claimingFirst(in.game(), in.seat())),
                new Benefit(
                        CardUse.LEADERSHIP_ENDURANCE,
                        Endurance.leadershipValue(
                                in.seat(), Math.min(StrategyCardRules.LEADERSHIP_TOKENS, in.reinforcements()))));
    }

    static boolean fundsWantedFollows(PlanInputs in) {
        return followsFunded(in) > 0;
    }

    static double fundedFollowsValue(PlanInputs in) {
        return followsFunded(in) * CardValueScale.HELD_TOKEN;
    }

    private static int followsFunded(PlanInputs in) {
        if (in.reinforcements() <= 0) return 0;
        Player seat = in.seat();
        int spendable = seat.getStrategicCC() - ScarToken.kept(in.game(), seat);
        int missing = StrategyCardRules.strategyTokensWantedForFollows(in.game(), seat) - spendable;
        return Math.max(0, Math.min(missing, Math.min(StrategyCardRules.LEADERSHIP_TOKENS, in.reinforcements())));
    }
}
