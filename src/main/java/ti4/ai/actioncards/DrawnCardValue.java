package ti4.ai.actioncards;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.ValueScale;
import ti4.ai.nekro.NekroLeaderRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class DrawnCardValue {

    static final int MALLEON_TRADE_GOODS = 2;
    private static final ThreadLocal<Boolean> JUDGING_THE_HAND = ThreadLocal.withInitial(() -> false);

    public static double of(Game game, Player seat, int cards) {
        return of(game, seat, cards, ActionCardPrior.AVERAGE_CARD);
    }

    public static double of(Game game, Player seat, int cards, double perCard) {
        if (cards <= 0) return 0;
        double first = malleonWantsACard(game, seat) ? Math.max(perCard, malleonValue()) : perCard;
        return first + (cards - 1) * perCard;
    }

    private static double malleonValue() {
        return MALLEON_TRADE_GOODS * ValueScale.TRADE_GOOD;
    }

    private static boolean malleonWantsACard(Game game, Player seat) {
        if (!NekroLeaderRules.ownsReadyAgent(seat) || JUDGING_THE_HAND.get()) return false;
        JUDGING_THE_HAND.set(true);
        try {
            return !ActionCardValue.hasSpareCard(game, seat);
        } finally {
            JUDGING_THE_HAND.set(false);
        }
    }
}
