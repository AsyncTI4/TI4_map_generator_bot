package ti4.ai.strategy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class OverruleChoice {

    private static final Set<StrategyCard> PLAYABLE = Set.of(StrategyCard.IMPERIAL, StrategyCard.TECHNOLOGY);
    private static final Map<StrategyCard, Double> HUMAN_SHARE =
            Map.of(StrategyCard.IMPERIAL, 0.45, StrategyCard.TECHNOLOGY, 0.25);

    public static boolean canPlay(Game game, int initiative) {
        return initiative > 0
                && !game.getPlayedSCs().contains(initiative)
                && PLAYABLE.contains(StrategyCard.of(game, initiative));
    }

    public static Optional<Double> value(Game game, Player seat, int initiative) {
        if (!canPlay(game, initiative)) return Optional.empty();
        StrategyCard card = StrategyCard.of(game, initiative);
        CardPlan plan = StrategyCardPlanner.plan(PlanInputs.of(game, seat, PlanMoment.ACTION), initiative);
        return Optional.of(plan.value() + CardValueScale.PRIOR_WEIGHT * HUMAN_SHARE.getOrDefault(card, 0.0));
    }
}
