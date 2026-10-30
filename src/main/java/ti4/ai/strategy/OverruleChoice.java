package ti4.ai.strategy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.ScoringStakes.OverrulePlay;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.secrets.SecretDraws;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class OverruleChoice {

    public record Option(int initiative, double value, boolean pointsPlay, boolean wins) {}

    private static final String OVERRULE = "overrule";
    private static final int ONE_POINT = 1;
    private static final Set<StrategyCard> PLAYABLE = Set.of(StrategyCard.IMPERIAL, StrategyCard.TECHNOLOGY);
    private static final Map<StrategyCard, Double> HUMAN_SHARE =
            Map.of(StrategyCard.IMPERIAL, 0.50, StrategyCard.TECHNOLOGY, 0.16);

    public static boolean canPlay(Game game, int initiative) {
        return initiative > 0
                && !game.getPlayedSCs().contains(initiative)
                && PLAYABLE.contains(StrategyCard.of(game, initiative));
    }

    public static Optional<Option> option(Game game, Player seat, int initiative) {
        if (!canPlay(game, initiative)) return Optional.empty();
        StrategyCard card = StrategyCard.of(game, initiative);
        double nudge = CardValueScale.PRIOR_WEIGHT * HUMAN_SHARE.getOrDefault(card, 0.0);
        if (card == StrategyCard.IMPERIAL) return Optional.of(imperial(game, seat, initiative, nudge));
        CardPlan plan = StrategyCardPlanner.plan(PlanInputs.of(game, seat, PlanMoment.ACTION), initiative);
        return Optional.of(new Option(initiative, plan.value() + nudge, false, false));
    }

    public static boolean takesMecatolFirst(Game game, Player seat) {
        return ImperialUses.takesMecatolFirst(PlanInputs.of(game, seat, PlanMoment.ACTION));
    }

    public static boolean canOverrule(Game game, Player seat) {
        return !game.isFowMode()
                && seat.getActionCards().containsKey(OVERRULE)
                && ActionCardRules.mayPlayNow(game, seat);
    }

    public static boolean overrulesOwnImperialFirst(Game game, Player seat) {
        return canOverrule(game, seat)
                && ScoringStakes.lateGame(game)
                && ScoringStakes.holdsUnplayedImperial(game, seat)
                && ScoringStakes.overrulePoints(game, seat) >= ONE_POINT;
    }

    private static Option imperial(Game game, Player seat, int initiative, double nudge) {
        OverrulePlay play = ScoringStakes.overrulePlay(game, seat);
        double points = ScoringStakes.scoreValue(game, seat, play.points());
        boolean pointsPlay = play.points() >= ONE_POINT || play.wins();
        return new Option(initiative, points + secret(game, seat) + nudge, pointsPlay, play.wins());
    }

    private static double secret(Game game, Player seat) {
        boolean drawsSecret = !seat.controlsMecatol(true) && SecretDraws.worthDrawing(game, seat);
        return drawsSecret ? SecretDrawValue.of(game, seat) : 0;
    }
}
