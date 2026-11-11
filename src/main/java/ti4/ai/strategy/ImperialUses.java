package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.scoring.ObjectiveOdds;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.secrets.SecretDraws;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class ImperialUses {

    private static final int FIRST_IMPERIAL_ROUND = 2;

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        Game game = in.game();
        Player seat = in.seat();
        boolean holdsMecatol = seat.controlsMecatol(true);
        PointsSplit points = pointsSplit(in);
        return List.of(
                new Benefit(CardUse.IMPERIAL_SCORE, points.score()),
                new Benefit(CardUse.IMPERIAL_MECATOL, points.mecatol(), mecatolPosition(game)),
                new Benefit(
                        CardUse.IMPERIAL_SECRET,
                        !holdsMecatol && SecretDraws.worthDrawing(game, seat) ? secretOverFollowing(in) : 0));
    }

    private static double secretOverFollowing(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        double draw = SecretDrawValue.of(game, seat);
        if (seat.getStrategicCC() <= 0 || isLastToPick(in)) return draw;
        return Math.min(draw, CardValueScale.STRATEGY_TOKEN);
    }

    private static boolean isLastToPick(PlanInputs in) {
        Game game = in.game();
        return in.moment() == PlanMoment.PICK
                && in.round() >= FIRST_IMPERIAL_ROUND
                && game.getRealPlayers().stream()
                        .filter(other -> other != in.seat())
                        .noneMatch(other -> other.getSCs().size() < Math.max(1, game.getStrategyCardsPerPlayer()));
    }

    static boolean holdsThreatenedMecatol(PlanInputs in) {
        return in.seat().controlsMecatol(true) && in.isThreatened(in.game().getMecatolTile());
    }

    private static double mecatolValue(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        if (seat.controlsMecatol(true)) {
            return CardValueScale.VICTORY_POINT
                    * (in.isThreatened(game.getMecatolTile()) ? CardValueScale.MECATOL_THREATENED_SHARE : 1);
        }
        return takesMecatolThisRound(in) ? CardValueScale.MECATOL_TAKE_SHARE * CardValueScale.VICTORY_POINT : 0;
    }

    static boolean takesMecatolFirst(PlanInputs in) {
        Player seat = in.seat();
        if (seat.controlsMecatol(true) || in.ranked().isEmpty()) return false;
        if (!TacticalPlanner.hasTokensForAnAction(in.game(), seat)) return false;
        return takesMecatol(in.ranked().getFirst(), mecatolPosition(in.game()));
    }

    static boolean winsByScoring(PlanInputs in) {
        int best = bestScorablePublic(in);
        return best > 0 && in.seat().getTotalVictoryPoints() + best >= in.game().getVp();
    }

    private static int bestScorablePublic(PlanInputs in) {
        return ObjectivePolicy.scorablePublics(in.game(), in.seat()).stream()
                .mapToInt(ObjectivePolicy::victoryPoints)
                .max()
                .orElse(0);
    }

    private static boolean takesMecatolThisRound(PlanInputs in) {
        String mecatol = mecatolPosition(in.game());
        if (mecatol.isEmpty()) return false;
        return takesCustodiansThisRound(in.game(), in.seat())
                || in.ranked().stream().anyMatch(plan -> takesMecatol(plan, mecatol));
    }

    private static boolean takesMecatol(TacticalPlan plan, String mecatol) {
        boolean takes = plan.kind() == TacticalPlan.Kind.EXPAND || plan.kind() == TacticalPlan.Kind.ATTACK;
        return takes && !mecatol.isEmpty() && plan.target().equals(mecatol);
    }

    private static String mecatolPosition(Game game) {
        Tile mecatol = game.getMecatolTile();
        return mecatol == null ? "" : mecatol.getPosition();
    }

    private record PointsSplit(double score, double mecatol) {}

    private static PointsSplit pointsSplit(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        double mecatol = mecatolValue(in);
        if (!pointsAware(game, seat)) {
            return new PointsSplit(
                    CardValueScale.VICTORY_POINT * ObjectiveOdds.extraScoreWithImperial(game, seat), mecatol);
        }
        int heldMecatol = seat.controlsMecatol(true) ? 1 : 0;
        ObjectiveOdds.ImperialOdds odds =
                ObjectiveOdds.imperial(game, seat, heldMecatol, secretChance(game, viewer(game, seat), seat));
        double flat = mecatol + CardValueScale.VICTORY_POINT * odds.extraPoints();
        double stake =
                ScoringStakes.scoreValue(game, seat, mecatol / CardValueScale.VICTORY_POINT + odds.extraPoints());
        double swing = ScoringStakes.WIN_VALUE * odds.winSwing();
        double worth = Math.max(flat, Math.max(stake, swing));
        boolean premiumOnMecatol =
                holdsThreatenedMecatol(in) || (mecatol > 0 && !ObjectiveOdds.imperialScoresAnExtraPublic(game, seat));
        return premiumOnMecatol ? new PointsSplit(0, worth) : new PointsSplit(worth - mecatol, mecatol);
    }

    static boolean gainsPointTempo(PlanInputs in) {
        return mecatolValue(in) > 0 || ObjectiveOdds.imperialScoresAnExtraPublic(in.game(), in.seat());
    }

    static boolean winsAtOnce(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        int mecatol = seat.controlsMecatol(true) ? 1 : 0;
        return game.getVp() > 0 && seat.getTotalVictoryPoints() + mecatol + bestScorablePublic(in) >= game.getVp();
    }

    static double secretChance(Game game, Player viewer, Player player) {
        if (player == viewer && !DecidingSeat.knowsHandOf(player)) return 0;
        return SecretOdds.chanceReady(game, viewer, player);
    }

    static boolean pointsAware(Game game, Player seat) {
        return AiExperiments.enabled(viewer(game, seat), AiExperiments.Experiment.IMPERIAL_POINTS);
    }

    static Player viewer(Game game, Player seat) {
        return DecidingSeat.orSelf(game, seat);
    }

    private static boolean takesCustodiansThisRound(Game game, Player seat) {
        return TokenPurchase.custodiansWithinReach(game, seat)
                && Wallet.of(game, seat).canPay(SpendCost.influence(CustodiansRace.cost(seat)));
    }
}
