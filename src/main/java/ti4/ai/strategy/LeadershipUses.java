package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class LeadershipUses {

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        return List.of(
                new Benefit(CardUse.LEADERSHIP_TOKENS, TokenValue.tacticTokens(in, StrategyCardRules.LEADERSHIP_TOKENS)),
                new Benefit(CardUse.LEADERSHIP_TEMPO, racesForCustodians(in) ? CardValueScale.CUSTODIANS_RACE_TEMPO : 0));
    }

    private static boolean racesForCustodians(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        if (game.isCustodiansScored() || !TokenPurchase.custodiansWithinReach(game, seat)) return false;
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .anyMatch(other -> TokenPurchase.custodiansWithinReach(game, other));
    }
}
