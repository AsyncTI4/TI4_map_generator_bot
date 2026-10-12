package ti4.ai.strategy;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class CardTiming {

    private static final int PUBLICS_FOR_IMPERIAL_NOW = 2;
    private static final int FIRST_ROUND_FOR_EARLY_PDS = 2;
    private static final int READY_USE_WORTH_PLAYING = 3;
    private static final double TE_WARFARE_REACTIVATION_MIN = 2 * TacticalPlanner.MIN_SCORE;

    enum Timing {
        NOW,
        FALLBACK
    }

    static Timing of(PlanInputs in, CardPlan plan) {
        if (plan.card() == StrategyCard.WARFARE) return warfare(in, plan);
        if (plan.card() == StrategyCard.IMPERIAL && yieldsToOverrule(in)) return Timing.FALLBACK;
        Optional<Benefit> main = plan.main();
        if (main.isEmpty()) return Timing.FALLBACK;
        return dueNow(in, main.get()) ? Timing.NOW : Timing.FALLBACK;
    }

    private static boolean dueNow(PlanInputs in, Benefit main) {
        Player seat = in.seat();
        return switch (main.use()) {
            case LEADERSHIP_TOKENS, TECHNOLOGY_PROPAGATION ->
                seat.getTacticalCC() == 0 && !in.ranked().isEmpty();
            case DIPLOMACY_PROTECT -> main.hasTarget() && in.isThreatened(tileAt(in.game(), main.target()));
            case DIPLOMACY_READY -> main.value() >= CardValueScale.READY_SHARE * READY_USE_WORTH_PLAYING;
            case CONSTRUCTION_PRODUCE -> main.value() >= in.bestScore();
            case CONSTRUCTION_PDS -> main.hasTarget() && in.round() >= FIRST_ROUND_FOR_EARLY_PDS;
            case CONSTRUCTION_DOCK -> in.round() <= CardValueScale.LAST_EARLY_DOCK_ROUND;
            case IMPERIAL_MECATOL -> ImperialUses.holdsThreatenedMecatol(in);
            case IMPERIAL_SCORE -> imperialScoresNow(in);
            default -> false;
        };
    }

    private static boolean yieldsToOverrule(PlanInputs in) {
        return OverruleChoice.overrulesOwnImperialFirst(in.game(), in.seat());
    }

    private static boolean imperialScoresNow(PlanInputs in) {
        if (ImperialUses.takesMecatolFirst(in) && !ImperialUses.winsByScoring(in)) return false;
        return ObjectivePolicy.scorablePublics(in.game(), in.seat()).size() >= PUBLICS_FOR_IMPERIAL_NOW;
    }

    private static Timing warfare(PlanInputs in, CardPlan plan) {
        if (CardVariant.of(in.game(), plan.initiative()) != CardVariant.THUNDERS_EDGE) {
            return WarfareUses.bestUnblockGain(in) >= CardValueScale.WARFARE_PLAY_MIN_GAIN
                    ? Timing.NOW
                    : Timing.FALLBACK;
        }
        Game game = in.game();
        Player seat = in.seat();
        Optional<TacticalPlan> warfare = TacticalPlanner.bestForWarfare(game, seat);
        if (warfare.isEmpty()) return Timing.FALLBACK;
        if (seat.getTacticalCC() == 0) return Timing.NOW;
        Tile target = tileAt(game, warfare.get().target());
        boolean reactivates = target != null && target.hasPlayerCC(seat);
        return reactivates && warfare.get().score() >= TE_WARFARE_REACTIVATION_MIN ? Timing.NOW : Timing.FALLBACK;
    }

    private static Tile tileAt(Game game, String position) {
        return game.getTileByPosition(position);
    }
}
