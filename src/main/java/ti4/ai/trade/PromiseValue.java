package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.promise.Presence;
import ti4.ai.promise.Promise;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class PromiseValue {

    static double protection(Game game, Player buyer, Player seller, Promise.Term term, String target) {
        return switch (term) {
            case VOTE_FOR -> 0;
            case NO_ATTACK ->
                CeasefireValue.DURESS_SHARE
                        * threatened(game, buyer, seller).stream()
                                .mapToDouble(tile -> loss(game, buyer, tile))
                                .max()
                                .orElse(0);
            case STAY_OUT -> {
                Tile tile = game.getTileByPosition(target);
                yield tile != null && exposed(game, buyer, seller, tile)
                        ? CeasefireValue.DURESS_SHARE * loss(game, buyer, tile)
                        : 0;
            }
        };
    }

    static Optional<Tile> mostExposed(Game game, Player buyer, Player seller) {
        return threatened(game, buyer, seller).stream()
                .max(Comparator.comparingDouble((Tile tile) -> loss(game, buyer, tile))
                        .thenComparing(Tile::getPosition));
    }

    static double restraintCost(Game game, Player promiser, Promise promise) {
        List<TacticalPlan> plans = TacticalPlanner.ranked(game, promiser);
        double best = plans.stream().mapToDouble(TacticalPlan::score).max().orElse(0);
        double allowed = plans.stream()
                .filter(plan -> !promise.forbidsActivating(game, game.getTileByPosition(plan.target())))
                .mapToDouble(TacticalPlan::score)
                .max()
                .orElse(0);
        return Math.max(0, best) - Math.max(0, allowed);
    }

    private static List<Tile> threatened(Game game, Player buyer, Player seller) {
        return game.getTileMap().values().stream()
                .filter(tile -> exposed(game, buyer, seller, tile))
                .toList();
    }

    private static boolean exposed(Game game, Player buyer, Player seller, Tile tile) {
        return Presence.at(buyer, tile) && CeasefireValue.outgunned(game, buyer, seller, tile);
    }

    private static double loss(Game game, Player buyer, Tile tile) {
        return Extortion.loss(game, buyer, tile, Extortion.pointsAtRisk(game, buyer, tile));
    }
}
