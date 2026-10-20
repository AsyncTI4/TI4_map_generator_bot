package ti4.ai.eval;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class IconStake {

    public static final String SEIZE_AN_ICON = "sai";

    public static boolean seeks(Game game, Player seat) {
        return seat.getSecretsUnscored().containsKey(SEIZE_AN_ICON)
                && ListPlayerInfoService.getPlayerProgressOnObjective(SEIZE_AN_ICON, game, seat) == 0;
    }

    public static boolean behindCustodians(Game game, Player seat) {
        return seeks(game, seat)
                && game.getTileMap().values().stream()
                        .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                        .anyMatch(planet -> planet.isLegendary() && BoardView.hasCustodians(planet));
    }

    public static boolean marks(Game game, Player seat, Tile tile) {
        return seeks(game, seat) && holdsLegendaryPlanetOf(game, seat, tile, false);
    }

    public static double freeValue(Game game, Player seat, Tile tile) {
        boolean prize = seeks(game, seat) && holdsLegendaryPlanetOf(game, seat, tile, true);
        return prize ? ObjectiveValue.VICTORY_POINT_VALUE : 0;
    }

    public static List<Tile> freeTiles(Game game, Player seat) {
        if (!seeks(game, seat)) return List.of();
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isMecatol(game))
                .filter(tile -> !BoardView.hasEnemyUnits(game, seat, tile))
                .filter(tile -> holdsLegendaryPlanetOf(game, seat, tile, true))
                .toList();
    }

    public static Comparator<Planet> claimOrder(Game game, Player seat) {
        boolean seeking = seeks(game, seat);
        return Comparator.comparingInt((Planet planet) -> seeking && planet.isLegendary() ? 0 : 1)
                .thenComparing(
                        Comparator.comparingDouble(BoardView::planetValue).reversed());
    }

    private static boolean holdsLegendaryPlanetOf(Game game, Player seat, Tile tile, boolean freeOnly) {
        return tile.getPlanetUnitHolders().stream()
                .filter(Planet::isLegendary)
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .anyMatch(planet -> !freeOnly || BoardView.controller(game, planet.getName()) == null);
    }
}
