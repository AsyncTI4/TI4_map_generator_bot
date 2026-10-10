package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class TokenPurchase {

    static final int INFLUENCE_PER_TOKEN = 3;
    static final int MAX_TOKENS = 3;
    static final int CUSTODIANS_COST = 6;

    record Purchase(int tokens, Wallet.Payment payment) {}

    public static SpendCost influenceToKeep(Game game, Player seat, SpendCost scoringReserve) {
        Wallet wallet = Wallet.of(game, seat);
        SpendCost custodians = custodiansReserve(game, seat);
        SpendCost kept = wallet.canPay(scoringReserve.plus(custodians)) ? custodians : SpendCost.NONE;
        return kept.plus(pendingLeadershipPurchase(game, seat, wallet));
    }

    private static SpendCost pendingLeadershipPurchase(Game game, Player seat, Wallet wallet) {
        Optional<Integer> leadership = game.getRealPlayers().stream()
                .flatMap(player -> player.getSCs().stream())
                .filter(card -> StrategyCard.of(game, card) == StrategyCard.LEADERSHIP)
                .filter(card -> !game.getPlayedSCs().contains(card) || !seat.hasFollowedSC(card))
                .findFirst();
        if (leadership.isEmpty()) return SpendCost.NONE;
        int reinforcements = StrategyCardRules.reinforcements(game, seat);
        boolean holdsIt = seat.getSCs().contains(leadership.get());
        if (holdsIt && game.getPlayedSCs().contains(leadership.get())) return SpendCost.NONE;
        int free = holdsIt ? Math.min(StrategyCardRules.LEADERSHIP_TOKENS, reinforcements) : 0;
        return best(game, seat, reinforcements - free, wallet)
                .map(purchase -> SpendCost.influence(INFLUENCE_PER_TOKEN * purchase.tokens()))
                .orElse(SpendCost.NONE);
    }

    static Optional<Purchase> best(Game game, Player seat, int reinforcements) {
        return best(game, seat, reinforcements, Wallet.of(game, seat));
    }

    static Optional<Purchase> best(Game game, Player seat, int reinforcements, Wallet wallet) {
        Wallet influencePlanets = new Wallet(influenceLeaning(wallet), 0, 0, 0);
        SpendCost keep = ScoringReserve.of(game, seat).plus(custodiansReserve(game, seat));
        for (int tokens = Math.min(MAX_TOKENS, reinforcements); tokens > 0; tokens--) {
            Optional<Wallet.Payment> payment = influencePlanets
                    .plan(SpendCost.influence(INFLUENCE_PER_TOKEN * tokens))
                    .filter(plan -> wallet.without(plan).canPay(keep));
            if (payment.isPresent()) return Optional.of(new Purchase(tokens, payment.get()));
        }
        return Optional.empty();
    }

    private static List<Wallet.PlanetValue> influenceLeaning(Wallet wallet) {
        return wallet.planets().stream()
                .filter(planet -> planet.influence() > planet.resources())
                .toList();
    }

    private static SpendCost custodiansReserve(Game game, Player seat) {
        return custodiansWithinReach(game, seat) ? SpendCost.influence(CUSTODIANS_COST) : SpendCost.NONE;
    }

    static boolean custodiansWithinReach(Game game, Player seat) {
        Optional<Tile> mecatol = game.getTileMap().values().stream()
                .filter(tile -> tile.isMecatol(game))
                .filter(tile -> BoardView.planets(tile).stream().anyMatch(BoardView::hasCustodians))
                .findFirst();
        if (mecatol.isEmpty()) return false;
        int move = BoardView.moveValueWithGravityDrive(seat, UnitType.Carrier);
        for (Tile tile : game.getTileMap().values()) {
            if (!BoardView.hasOwnShips(seat, tile)) continue;
            if (MovementGraph.reach(game, seat, tile.getPosition(), move)
                    .containsKey(mecatol.get().getPosition())) {
                return true;
            }
        }
        return false;
    }
}
