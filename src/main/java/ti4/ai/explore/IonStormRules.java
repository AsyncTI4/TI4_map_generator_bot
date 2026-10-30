package ti4.ai.explore;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.model.UnitModel;
import ti4.model.WormholeModel.Wormhole;

@UtilityClass
class IonStormRules {

    static final String ALPHA = "alpha";
    static final String BETA = "beta";
    private static final double FREE_PLANET_SHARE = 0.25;
    private static final double OWN_FLEET_LINK = 0.5;
    private static final double ENEMY_FLEET_SHARE = 0.1;

    static String sideFor(Game game, Player seat, Tile storm) {
        double alpha = score(game, seat, storm, Wormhole.ALPHA);
        double beta = score(game, seat, storm, Wormhole.BETA);
        return beta > alpha ? BETA : ALPHA;
    }

    private static double score(Game game, Player seat, Tile storm, Wormhole side) {
        boolean exposed = holdsPlanetNear(game, seat, storm);
        double score = 0;
        for (Tile other : game.getTileMap().values()) {
            if (other == storm || !other.getWormholes(game).contains(side)) continue;
            score += FREE_PLANET_SHARE * freePlanetValue(game, other);
            if (BoardView.hasOwnShips(seat, other)) score += OWN_FLEET_LINK;
            if (exposed) score -= ENEMY_FLEET_SHARE * enemyFleetCost(game, seat, other);
        }
        return score;
    }

    private static boolean holdsPlanetNear(Game game, Player seat, Tile storm) {
        return FoWHelper.getAdjacentTiles(game, storm.getPosition(), seat, false).stream()
                        .map(game::getTileByPosition)
                        .anyMatch(tile -> holdsPlanet(seat, tile))
                || holdsPlanet(seat, storm);
    }

    private static boolean holdsPlanet(Player seat, Tile tile) {
        return tile != null
                && tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> seat.getPlanets().contains(planet.getName()));
    }

    private static double freePlanetValue(Game game, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                .mapToDouble(BoardView::planetValue)
                .sum();
    }

    private static double enemyFleetCost(Game game, Player seat, Tile tile) {
        double total = 0;
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (var ship : BoardView.ships(BoardView.space(tile), other).entrySet()) {
                total += ship.getValue()
                        * BoardView.model(other, ship.getKey())
                                .map(UnitModel::getCost)
                                .orElse(0f);
            }
        }
        return total;
    }
}
