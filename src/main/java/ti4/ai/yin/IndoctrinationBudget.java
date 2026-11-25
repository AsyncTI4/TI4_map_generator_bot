package ti4.ai.yin;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.tactical.GroundDrops;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
class IndoctrinationBudget {

    static final int MAX_CONVERSIONS = DannelPlan.MAX_INFANTRY;

    static int conversions(Game game, Player seat) {
        if (!seat.hasAbility(IndoctrinationRules.ABILITY)) return 0;
        int cost = cost(game, seat);
        int conversions = 0;
        while (conversions < MAX_CONVERSIONS && affordable(game, seat, (conversions + 1) * cost)) conversions++;
        return conversions;
    }

    static UnitType replacement(Game game, Player seat) {
        return mechReplacement(game, seat) ? UnitType.Mech : UnitType.Infantry;
    }

    private static int cost(Game game, Player seat) {
        return mechReplacement(game, seat)
                ? IndoctrinationRules.MECH_INFLUENCE
                : IndoctrinationRules.INFANTRY_INFLUENCE;
    }

    private static boolean mechReplacement(Game game, Player seat) {
        return seat.hasUnit(IndoctrinationRules.MECH)
                && GroundDrops.mechAvailable(game, seat)
                && affordable(game, seat, IndoctrinationRules.MECH_INFLUENCE);
    }

    private static boolean affordable(Game game, Player seat, int influence) {
        return ScoringReserve.planAfterReserve(game, seat, SpendCost.influence(influence))
                .isPresent();
    }
}
