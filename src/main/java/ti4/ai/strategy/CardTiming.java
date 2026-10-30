package ti4.ai.strategy;

import java.util.List;
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
    private static final int LAST_ADVANCE_ROUND = 1;
    private static final String GRAVITY_DRIVE = "gd";
    private static final List<CardUse> EXPEDITION_USES =
            List.of(CardUse.TRADE_EXPEDITION, CardUse.POLITICS_EXPEDITION, CardUse.DIPLOMACY_EXPEDITION);

    enum Timing {
        NOW,
        FALLBACK
    }

    static Timing of(PlanInputs in, CardPlan plan) {
        if (movesOutBeforeConstruction(in, plan)) return Timing.FALLBACK;
        if (opensTheRound(in, plan)) return Timing.NOW;
        if (claimsWithPeaceAccords(in, plan)) return Timing.NOW;
        if (plan.card() == StrategyCard.LEADERSHIP && LeadershipUses.fundsWantedFollows(in)) return Timing.NOW;
        if (plan.card() == StrategyCard.WARFARE) return warfare(in, plan);
        if (plan.card() == StrategyCard.IMPERIAL && yieldsToOverrule(in)) return Timing.FALLBACK;
        Optional<Benefit> main = plan.main();
        if (main.isEmpty()) return Timing.FALLBACK;
        return dueNow(in, plan, main.get()) || claimsExpedition(plan) ? Timing.NOW : Timing.FALLBACK;
    }

    private static boolean claimsExpedition(CardPlan plan) {
        return EXPEDITION_USES.stream()
                .anyMatch(use -> plan.benefit(use)
                        .filter(benefit -> benefit.value() >= CardValueScale.EXPEDITION_PLAY_MIN)
                        .isPresent());
    }

    private static boolean claimsWithPeaceAccords(PlanInputs in, CardPlan plan) {
        return plan.benefit(CardUse.DIPLOMACY_PEACE_ACCORDS)
                .filter(benefit -> benefit.value() >= Math.max(CardValueScale.PEACE_ACCORDS_PLAY_MIN, in.bestScore()))
                .isPresent();
    }

    private static boolean opensTheRound(PlanInputs in, CardPlan plan) {
        if (expandsTheOpening(plan)) return true;
        return plan.card() == StrategyCard.TECHNOLOGY && researchesGravityDriveForTheOpening(in.game(), in.seat());
    }

    private static boolean expandsTheOpening(CardPlan plan) {
        return plan.benefit(CardUse.OPENING_EXPANSION)
                .filter(benefit -> benefit.value() > 0)
                .isPresent();
    }

    private static boolean movesOutBeforeConstruction(PlanInputs in, CardPlan plan) {
        if (plan.card() != StrategyCard.CONSTRUCTION || !expandsTheOpening(plan)) return false;
        Game game = in.game();
        Player seat = in.seat();
        Tile home = seat.getHomeSystemTile();
        if (home == null || seat.getTacticalCC() <= 0) return false;
        if (ConstructionUses.movingOutOf(in.ranked(), home.getPosition()).isEmpty()) return false;
        return !OpeningOutlook.producesBeforeMoving(game, seat);
    }

    static boolean researchesGravityDriveForTheOpening(Game game, Player seat) {
        return OpeningOutlook.gain(game, seat, OpeningOutlook.Boost.GRAVITY_DRIVE) > 0
                && ResearchPolicy.bestResearchable(game, seat)
                        .filter(GRAVITY_DRIVE::equals)
                        .isPresent();
    }

    private static boolean dueNow(PlanInputs in, CardPlan plan, Benefit main) {
        Player seat = in.seat();
        return switch (main.use()) {
            case LEADERSHIP_TOKENS, TECHNOLOGY_PROPAGATION ->
                seat.getTacticalCC() == 0 && !in.ranked().isEmpty();
            case DIPLOMACY_PROTECT -> main.hasTarget() && in.isThreatened(tileAt(in.game(), main.target()));
            case DIPLOMACY_READY ->
                main.value() >= CardValueScale.READY_SHARE * READY_USE_WORTH_PLAYING && !readiesAfterSpending(in, plan);
            case CONSTRUCTION_PRODUCE -> main.value() >= in.bestScore();
            case TECHNOLOGY_RESEARCH -> TechnologyTiming.playsNow(in);
            case CONSTRUCTION_PDS -> main.hasTarget() && in.round() >= FIRST_ROUND_FOR_EARLY_PDS;
            case CONSTRUCTION_DOCK -> in.round() <= CardValueScale.LAST_EARLY_DOCK_ROUND;
            case IMPERIAL_MECATOL -> ImperialUses.holdsThreatenedMecatol(in);
            case IMPERIAL_SCORE -> imperialScoresNow(in);
            default -> false;
        };
    }

    private static boolean readiesAfterSpending(PlanInputs in, CardPlan plan) {
        Game game = in.game();
        Player seat = in.seat();
        return DiplomacyUses.revealsSpendObjective(game, seat)
                && !protectsThreatenedSystem(in, plan)
                && DiplomacyUse.spendingAhead(game, seat, !in.ranked().isEmpty());
    }

    private static boolean protectsThreatenedSystem(PlanInputs in, CardPlan plan) {
        return plan.benefit(CardUse.DIPLOMACY_PROTECT)
                .filter(benefit -> benefit.hasTarget() && in.isThreatened(tileAt(in.game(), benefit.target())))
                .isPresent();
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
        if (seat.getTacticalCC() == 0 || in.round() <= LAST_ADVANCE_ROUND) return Timing.NOW;
        Tile target = tileAt(game, warfare.get().target());
        boolean reactivates = target != null && target.hasPlayerCC(seat);
        if (reactivates) return warfare.get().score() >= TE_WARFARE_REACTIVATION_MIN ? Timing.NOW : Timing.FALLBACK;
        if (warfare.get().score() < TacticalPlanner.MIN_SCORE) return Timing.FALLBACK;
        return savesForAHomeReactivation(seat, plan) ? Timing.FALLBACK : Timing.NOW;
    }

    private static boolean savesForAHomeReactivation(Player seat, CardPlan plan) {
        Tile home = seat.getHomeSystemTile();
        if (home == null || home.hasPlayerCC(seat)) return false;
        return plan.main()
                .map(Benefit::use)
                .filter(use -> use == CardUse.WARFARE_DOUBLE_BUILD || use == CardUse.WARFARE_BUILD_THEN_MOVE)
                .isPresent();
    }

    private static Tile tileAt(Game game, String position) {
        return game.getTileByPosition(position);
    }
}
