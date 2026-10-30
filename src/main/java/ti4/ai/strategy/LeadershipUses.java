package ti4.ai.strategy;

import java.util.List;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;
import ti4.ai.endurance.Endurance;
import ti4.ai.eval.CustodiansRace.Order;
import ti4.ai.eval.CustodiansRace.Snapshot;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Player;

@UtilityClass
class LeadershipUses {

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        return List.of(
                new Benefit(
                        CardUse.LEADERSHIP_TOKENS, TokenValue.carriedTokens(in, StrategyCardRules.LEADERSHIP_TOKENS)),
                new Benefit(CardUse.LEADERSHIP_TEMPO, firstInTheRace(in)),
                new Benefit(
                        CardUse.LEADERSHIP_EXPEDITION,
                        CardValueScale.EXPEDITION_FIRST_SHARE * ExpeditionUnlock.claimingFirst(in.game(), in.seat())),
                new Benefit(
                        CardUse.LEADERSHIP_ENDURANCE,
                        Endurance.leadershipValue(
                                in.seat(), Math.min(StrategyCardRules.LEADERSHIP_TOKENS, in.reinforcements()))));
    }

    static boolean fundsWantedFollows(PlanInputs in) {
        if (in.reinforcements() <= 0) return false;
        Player seat = in.seat();
        int spendable = seat.getStrategicCC() - ScarToken.kept(in.game(), seat);
        return StrategyCardRules.strategyTokensWantedForFollows(in.game(), seat) > spendable;
    }

    private static double firstInTheRace(PlanInputs in) {
        Player seat = in.seat();
        Snapshot race = in.race();
        if (!race.isOpen() || seat.hasTheZeroToken()) return 0;
        OptionalInt arrival = race.arrival(seat);
        if (arrival.isEmpty() || arrival.getAsInt() != in.round()) return 0;
        double first = race.raceValue(seat, arrival, Order.SEAT_FIRST);
        double unknown = race.raceValue(seat, arrival, Order.UNKNOWN);
        return Math.clamp(first - unknown, 0, CardValueScale.LEADERSHIP_RACE_CAP * CardValueScale.VICTORY_POINT);
    }
}
