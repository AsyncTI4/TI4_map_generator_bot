package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.secrets.SecretValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.service.info.ListPlayerInfoService;

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

    public static boolean canPay(Player seat) {
        return seat.getStrategicCC() >= 1 || hasScepter(seat);
    }

    public static boolean canBuy(Game game, Player seat) {
        return kept(game, seat) > 0 && canPay(seat);
    }

    public static boolean completesAdaptNewStrategies(Game game, Player seat, String techAlias) {
        if (!seat.getSecretsUnscored().containsKey(ADAPT_NEW_STRATEGIES)
                || !ListPlayerInfoService.countsForAdaptNewStrategies(techAlias, game.isTwilightsFallMode())) {
            return false;
        }
        int threshold = ListPlayerInfoService.getObjectiveThreshold(ADAPT_NEW_STRATEGIES, game);
        return threshold > 0 && SecretValue.ownFactionTechnologies(seat) + 1 >= threshold;
    }

    private static boolean hasScepter(Player seat) {
        return seat.hasRelicReady("emelpar") || seat.hasRelicReady("absol_emelpar");
    }

    private static boolean shipsBeside(Game game, Player seat, Tile scar) {
        return FoWHelper.getAdjacentTiles(game, scar.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(tile -> tile != null && tile != scar && BoardView.hasOwnShips(seat, tile));
    }
}
