package ti4.service.transaction;

import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.emoji.TI4Emoji;

public final class DebtPoolIconService {

    private DebtPoolIconService() {}

    public static void assignMissingIcons(Game game) {
        for (Player player : game.getPlayers().values()) {
            for (Map.Entry<String, Map<String, Integer>> pool :
                    player.getAllDebtTokens().entrySet()) {
                if (hasTokens(pool.getValue()) && needsIcon(game, pool.getKey())) {
                    game.setDebtPoolIcon(pool.getKey(), defaultIcon(game, pool.getKey()));
                }
            }
        }
    }

    public static String defaultIcon(Game game, String poolName) {
        return TI4Emoji.getRandomizedEmoji(0, game.getName() + poolName).toString();
    }

    private static boolean hasTokens(Map<String, Integer> tokensByDebtor) {
        return tokensByDebtor.values().stream().anyMatch(count -> count > 0);
    }

    private static boolean needsIcon(Game game, String poolName) {
        return game.getDebtPoolIcon(poolName) == null && !Constants.DEBT_DEFAULT_POOL.equalsIgnoreCase(poolName);
    }
}
