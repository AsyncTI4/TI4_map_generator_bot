package ti4.ai.tactical;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.ai.scoring.WinRace;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TokenPrice {

    public record Prices(double held, double surplus, double stall) {

        static final Prices FREE = new Prices(0, 0, 0);

        boolean pays(TacticalPlan plan, boolean tokensToSpare) {
            double price = tokensToSpare && plan.kind() != Kind.PRODUCE ? surplus : held;
            return plan.score() + stall >= price;
        }
    }

    public static Prices of(Game game, Player seat) {
        double stall = Stall.actionValue(game, seat);
        if (tokensLoseTheirWorth(game, seat)) return new Prices(0, 0, stall);
        return new Prices(CardValueScale.HELD_TOKEN, CardValueScale.TOKEN_FLOOR, stall);
    }

    static boolean tokensLoseTheirWorth(Game game, Player seat) {
        return GameHorizon.roundsLeft(game) <= 1
                || !WinRace.winnersBefore(game, seat).isEmpty();
    }

    static List<TacticalPlan> worthTheirToken(List<TacticalPlan> plans, int spareTokens, Prices prices) {
        long worthwhile = plans.stream()
                .filter(plan -> prices.pays(plan, false))
                .map(TacticalPlan::target)
                .distinct()
                .count();
        boolean tokensToSpare = spareTokens > worthwhile;
        return plans.stream().filter(plan -> prices.pays(plan, tokensToSpare)).toList();
    }
}
