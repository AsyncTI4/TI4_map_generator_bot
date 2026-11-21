package ti4.ai.strategy;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class LeadershipStall {

    public static Optional<Double> value(Game game, Player seat) {
        if (seat.getTacticalCC() > 0 || !leadershipStillToCome(game, seat)) return Optional.empty();
        int reinforcements = StrategyCardRules.reinforcements(game, seat);
        Optional<TokenPurchase.Purchase> purchase = TokenPurchase.best(game, seat, reinforcements);
        if (purchase.isEmpty()) return Optional.empty();
        double actions = TokenValue.plannedActions(
                PlanInputs.of(game, seat, PlanMoment.ACTION), purchase.get().tokens());
        return actions > 0 ? Optional.of(actions) : Optional.empty();
    }

    private static boolean leadershipStillToCome(Game game, Player seat) {
        return game.getSCList().stream()
                .filter(initiative -> StrategyCard.of(game, initiative) == StrategyCard.LEADERSHIP)
                .anyMatch(initiative -> !game.getPlayedSCs().contains(initiative)
                        && !seat.getSCs().contains(initiative));
    }
}
