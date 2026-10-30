package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveOdds;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.secrets.SecretDraws;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class ImperialUses {

    private static final int FIRST_IMPERIAL_ROUND = 2;

    private static final int CUSTODIANS_COST = 6;

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        Game game = in.game();
        Player seat = in.seat();
        boolean holdsMecatol = seat.controlsMecatol(true);
        return List.of(
                new Benefit(CardUse.IMPERIAL_SCORE, CardValueScale.VICTORY_POINT * extraPublicScore(game, seat)),
                new Benefit(CardUse.IMPERIAL_MECATOL, mecatolValue(in), mecatolPosition(game)),
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
        int best = ObjectivePolicy.scorablePublics(in.game(), in.seat()).stream()
                .mapToInt(ObjectivePolicy::victoryPoints)
                .max()
                .orElse(0);
        return best > 0 && in.seat().getTotalVictoryPoints() + best >= in.game().getVp();
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

    private static double extraPublicScore(Game game, Player seat) {
        return ObjectiveOdds.extraScoreWithImperial(game, seat);
    }

    private static boolean takesCustodiansThisRound(Game game, Player seat) {
        return TokenPurchase.custodiansWithinReach(game, seat)
                && Wallet.of(game, seat).canPay(SpendCost.influence(CUSTODIANS_COST));
    }
}
