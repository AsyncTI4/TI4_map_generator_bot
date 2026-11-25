package ti4.ai.tactical;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.Adjacency;
import ti4.ai.eval.MoveBudget;
import ti4.ai.eval.MovementCard;
import ti4.ai.eval.MovementGraph;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class MovementCardGates {

    static boolean worthAPass(Game game, Player seat, MovementCard card, List<Tile> origins) {
        return switch (card) {
            case FLANK_SPEED -> true;
            case LOST_STAR_CHART -> chartOpensALink(game, seat, origins);
            case NAV_SUITE -> anomalyInTheWay(game, seat, origins);
        };
    }

    private static boolean chartOpensALink(Game game, Player seat, List<Tile> origins) {
        Set<String> charted = Adjacency.lostStarSystems(game, seat);
        if (charted.size() < 2) return false;
        boolean unlinked = charted.stream()
                .anyMatch(
                        position -> !Adjacency.forMovement(game, seat, position).containsAll(charted));
        if (!unlinked) return false;
        for (Tile origin : origins) {
            int budget = MoveBudget.searchBudget(game, seat, origin);
            if (budget <= 0) continue;
            Map<String, Integer> reached = MovementGraph.distances(game, seat, origin.getPosition(), budget);
            if (reached.keySet().stream().anyMatch(charted::contains)) return true;
        }
        return false;
    }

    // TODO: Nav Suite is never played only to skip a gravity-rift roll; needsCard keeps just the plans it makes legal
    private static boolean anomalyInTheWay(Game game, Player seat, List<Tile> origins) {
        for (Tile origin : origins) {
            Map<String, Integer> now = reachable(game, seat, origin, true);
            Map<String, Integer> suited =
                    MovementCard.NAV_SUITE.assuming(seat, () -> reachable(game, seat, origin, false));
            for (Map.Entry<String, Integer> reached : suited.entrySet()) {
                if (reached.getValue() < now.getOrDefault(reached.getKey(), Integer.MAX_VALUE)) return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> reachable(Game game, Player seat, Tile origin, boolean throughRifts) {
        int budget = MoveBudget.searchBudget(game, seat, origin);
        if (budget <= 0) return Map.of();
        return throughRifts
                ? MovementGraph.riftingDistances(game, seat, origin.getPosition(), budget)
                : MovementGraph.distances(game, seat, origin.getPosition(), budget);
    }
}
