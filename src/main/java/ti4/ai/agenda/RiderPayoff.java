package ti4.ai.agenda;

import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.SpeakerTokenValue;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class RiderPayoff {

    public static final String IMPERIAL = "Imperial Rider";
    public static final String TRADE = "Trade Rider";
    public static final String POLITICS = "Politics Rider";
    private static final int TRADE_GOODS = 5;
    private static final int POLITICS_CARDS = 3;
    private static final int POINT = 1;
    private static final double OTHER_RIDER = 2 * ActionCardPrior.AVERAGE_CARD;

    public static double of(Game game, Player player, String rider) {
        return switch (rider) {
            case IMPERIAL -> ScoringStakes.scoreValue(game, player, POINT);
            case TRADE ->
                TRADE_GOODS + ScoringStakes.scoreValue(game, player, SpendUnlock.pointDelta(game, player, TRADE_GOODS));
            case POLITICS -> POLITICS_CARDS * ActionCardPrior.AVERAGE_CARD + SpeakerTokenValue.of(game, player);
            default -> OTHER_RIDER;
        };
    }

    public static boolean isImperial(String rider) {
        return IMPERIAL.equals(rider);
    }
}
