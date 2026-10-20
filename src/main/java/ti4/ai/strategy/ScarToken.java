package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
public class ScarToken {

    private static final String ADAPT_NEW_STRATEGIES = "ans";

    public static int kept(Game game, Player seat) {
        if (!seat.getSecretsUnscored().containsKey(ADAPT_NEW_STRATEGIES)) return 0;
        if (TechPath.factionTechsNeeded(seat).orElse(0) <= 0) return 0;
        boolean nearAScar = game.getTileMap().values().stream()
                .filter(tile -> tile.isScar(game))
                .anyMatch(scar -> BoardView.hasOwnShips(seat, scar) || shipsBeside(game, seat, scar));
        return nearAScar ? 1 : 0;
    }

    private static boolean shipsBeside(Game game, Player seat, Tile scar) {
        return FoWHelper.getAdjacentTiles(game, scar.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(tile -> tile != null && tile != scar && BoardView.hasOwnShips(seat, tile));
    }
}
