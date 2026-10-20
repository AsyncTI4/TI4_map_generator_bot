package ti4.ai.agenda;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.SpeakerTokenValue;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class RiderPayoff {

    public static final String IMPERIAL = "Imperial Rider";
    public static final String TRADE = "Trade Rider";
    public static final String POLITICS = "Politics Rider";
    public static final String TECHNOLOGY = "Technology Rider";
    public static final String LEADERSHIP = "Leadership Rider";
    private static final int TRADE_GOODS = 5;
    private static final int POLITICS_CARDS = 3;
    private static final int LEADERSHIP_TOKENS = 3;
    private static final int POINT = 1;
    private static final Set<String> NEEDS_THE_HAND = Set.of(TECHNOLOGY, LEADERSHIP);
    private static final double OTHER_RIDER = 2 * ActionCardPrior.AVERAGE_CARD;

    public static double of(Game game, Player viewer, Player owner, String rider) {
        if (owner != viewer && NEEDS_THE_HAND.contains(rider)) return OTHER_RIDER;
        return of(game, owner, rider);
    }

    public static double of(Game game, Player player, String rider) {
        return switch (rider) {
            case IMPERIAL -> ScoringStakes.scoreValue(game, player, POINT);
            case TRADE ->
                TRADE_GOODS + ScoringStakes.scoreValue(game, player, SpendUnlock.pointDelta(game, player, TRADE_GOODS));
            case POLITICS -> POLITICS_CARDS * ActionCardPrior.AVERAGE_CARD + SpeakerTokenValue.of(game, player);
            case TECHNOLOGY ->
                StrategyCardRules.cannotResearch(player)
                        ? CommandTokenValue.tactic(game, player, LEADERSHIP_TOKENS)
                        : ResearchPolicy.freeResearchWorth(game, player);
            case LEADERSHIP -> CommandTokenValue.tactic(game, player, LEADERSHIP_TOKENS);
            default -> OTHER_RIDER;
        };
    }

    public static boolean isImperial(String rider) {
        return IMPERIAL.equals(rider);
    }
}
