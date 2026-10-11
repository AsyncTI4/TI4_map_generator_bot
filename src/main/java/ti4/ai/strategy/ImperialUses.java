package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.TacticalPlan;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class ImperialUses {

    private static final int STATUS_PHASE_PUBLIC_SCORES = 1;
    private static final int IMPERIAL_PUBLIC_SCORES = 1;
    private static final int RESERVED_SPEND_SCORES = 1;
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
                        !holdsMecatol && hasRoomForSecret(seat) ? CardValueScale.SECRET_DRAW : 0));
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

    private static boolean takesMecatolThisRound(PlanInputs in) {
        String mecatol = mecatolPosition(in.game());
        if (mecatol.isEmpty()) return false;
        return takesCustodiansThisRound(in.game(), in.seat())
                || in.ranked().stream()
                        .filter(plan ->
                                plan.kind() == TacticalPlan.Kind.EXPAND || plan.kind() == TacticalPlan.Kind.ATTACK)
                        .anyMatch(plan -> plan.target().equals(mecatol));
    }

    private static String mecatolPosition(Game game) {
        Tile mecatol = game.getMecatolTile();
        return mecatol == null ? "" : mecatol.getPosition();
    }

    private static double extraPublicScore(Game game, Player seat) {
        List<String> scorable = ObjectivePolicy.scorablePublics(game, seat);
        long free =
                scorable.stream().filter(id -> !ObjectiveCatalog.isSpend(id)).count();
        long spend = Math.min(RESERVED_SPEND_SCORES, scorable.size() - free);
        double expected = free + spend + CardValueScale.ONE_STEP_SHORT_CHANCE * oneStepShortPublics(game, seat);
        return Math.clamp(expected - STATUS_PHASE_PUBLIC_SCORES, 0, IMPERIAL_PUBLIC_SCORES);
    }

    private static long oneStepShortPublics(Game game, Player seat) {
        if (!Helper.canPlayerScorePOs(game, seat)) return 0;
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> Mapper.getPublicObjective(id) != null && !ObjectiveCatalog.isSpend(id))
                .filter(id -> !ObjectivePolicy.hasScored(game, seat, id))
                .filter(id -> isOneStepShort(game, seat, id))
                .count();
    }

    private static boolean isOneStepShort(Game game, Player seat, String objectiveId) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objectiveId, game);
        return threshold > 1
                && ListPlayerInfoService.getPlayerProgressOnObjective(objectiveId, game, seat) == threshold - 1;
    }

    private static boolean takesCustodiansThisRound(Game game, Player seat) {
        return TokenPurchase.custodiansWithinReach(game, seat)
                && Wallet.of(game, seat).canPay(SpendCost.influence(CUSTODIANS_COST));
    }

    private static boolean hasRoomForSecret(Player seat) {
        return seat.getSoScored() + seat.getSecretsUnscored().size() < seat.getMaxSOCount();
    }
}
