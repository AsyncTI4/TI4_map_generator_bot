package ti4.ai.strategy;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class OpeningNeeds {

    private static final String TECHNOLOGY_REQUESTED = "technologyRequested|";

    public static boolean wantsTechnologySoon(Game game, Player seat) {
        return seat.getStrategicCC() > 0
                && technologyHolder(game, seat).isPresent()
                && CardTiming.researchesGravityDriveForTheOpening(game, seat);
    }

    public static Optional<Player> technologyHolder(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && holdsUnplayedTechnology(game, other))
                .findFirst();
    }

    public static boolean holdsUnplayedTechnology(Game game, Player player) {
        return player.getSCs().stream()
                .anyMatch(initiative -> !game.getPlayedSCs().contains(initiative)
                        && StrategyCard.of(game, initiative) == StrategyCard.TECHNOLOGY);
    }

    public static boolean canFollowTechnologyAfterPaying(Game game, Player seat, int tradeGoods) {
        return canFollowTechnology(game, seat, tradeGoods, OpeningOutlook.raisableTradeGoods(game, seat));
    }

    static boolean canFollowTechnology(Game game, Player seat, int paying, int raisedTradeGoods) {
        Wallet wallet = Wallet.of(game, seat);
        Wallet raised = wallet.withTradeGoods(wallet.tradeGoods() + raisedTradeGoods);
        SpendCost follow = SpendCost.resources(StrategyCardRules.FOLLOW_TECH_COST + paying);
        return ScoringReserve.planAfterReserve(raised, ScoringReserve.of(game, seat), follow)
                .isPresent();
    }

    public static String technologyRequestedKey(Game game) {
        return TECHNOLOGY_REQUESTED + game.getRound();
    }
}
