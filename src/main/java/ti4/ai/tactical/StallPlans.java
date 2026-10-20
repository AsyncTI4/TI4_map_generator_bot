package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.endurance.Endurance;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;

@UtilityClass
public class StallPlans {

    private static final int HOME_RANK = 3;
    private static final int HELD_RANK = 2;
    private static final int EMPTY_RANK = 1;

    public static Optional<TacticalPlan> enduranceStall(Game game, Player seat) {
        double bonus = Endurance.stallBonus(game, seat);
        if (bonus <= 0 || !TacticalPlanner.hasTokensForAnAction(game, seat)) return Optional.empty();
        return game.getTileMap().values().stream()
                .filter(tile -> activatable(game, seat, tile))
                .filter(tile -> !BoardView.hasEnemyUnits(game, seat, tile))
                .max(Comparator.comparingInt((Tile tile) -> rank(seat, tile))
                        .thenComparing(Tile::getPosition, Comparator.reverseOrder()))
                .map(tile -> new TacticalPlan(Kind.POSITION, tile.getPosition(), List.of(), Map.of(), bonus));
    }

    private static boolean activatable(Game game, Player seat, Tile tile) {
        return !tile.hasPlayerCC(seat) && ButtonHelper.canActivateTile(game, seat, tile);
    }

    private static int rank(Player seat, Tile tile) {
        if (tile == seat.getHomeSystemTile()) return HOME_RANK;
        boolean holdsPlanet = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> seat.getPlanets().contains(planet.getName()));
        return holdsPlanet || BoardView.hasOwnShips(seat, tile) ? HELD_RANK : EMPTY_RANK;
    }
}
