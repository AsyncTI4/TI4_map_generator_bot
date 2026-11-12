package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
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
            case VOTE_FOR, DISCARD_AGENDA -> 0;
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
        return restraintCost(game, TacticalPlanner.ranked(game, promiser), promiser.getTacticalCC(), promise);
    }

    static double restraintCost(Game game, List<TacticalPlan> plans, int tacticalTokens, Promise promise) {
        Predicate<TacticalPlan> forbidden =
                plan -> promise.forbidsActivating(game, game.getTileByPosition(plan.target()));
        double forbiddenBest = bestScore(plans, forbidden);
        double allowed = bestScore(plans, forbidden.negate());
        long preferred = plans.stream()
                .filter(forbidden.negate())
                .filter(plan -> plan.score() > forbiddenBest)
                .map(TacticalPlan::target)
                .distinct()
                .count();
        if (tacticalTokens > preferred) return forbiddenBest;
        return Math.max(0, Math.max(forbiddenBest, allowed) - allowed);
    }

    private static double bestScore(List<TacticalPlan> plans, Predicate<TacticalPlan> filter) {
        return Math.max(
                0,
                plans.stream()
                        .filter(filter)
                        .mapToDouble(TacticalPlan::score)
                        .max()
                        .orElse(0));
    }

    private static List<Tile> threatened(Game game, Player buyer, Player seller) {
        return game.getTileMap().values().stream()
                .filter(tile -> exposed(game, buyer, seller, tile))
                .toList();
    }

    private static boolean exposed(Game game, Player buyer, Player seller, Tile tile) {
        return Presence.at(buyer, tile)
                && seller.getTacticalCC() > 0
                && !tile.hasPlayerCC(seller)
                && CeasefireValue.outgunned(game, buyer, seller, tile);
    }

    private static double loss(Game game, Player buyer, Tile tile) {
        return Extortion.loss(game, buyer, tile, Extortion.pointsAtRisk(game, buyer, tile));
    }
}
