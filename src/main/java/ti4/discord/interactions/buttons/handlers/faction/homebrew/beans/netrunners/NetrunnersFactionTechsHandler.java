package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class NetrunnersFactionTechsHandler {
    public static final String DATA_MINING_TECH = "benetrunnersdm";
    private static final String DATA_MINING_RESOLVED = "netrunnersDataMiningResolved";

    public static void resolveDataMining(Game game) {
        for (Player netrunner : game.getRealPlayers()) {
            if (!netrunner.hasTech(DATA_MINING_TECH)) continue;
            String key = DATA_MINING_RESOLVED + netrunner.getFaction();
            if (Integer.toString(game.getRound()).equals(game.getStoredValue(key))) continue;
            game.setStoredValue(key, Integer.toString(game.getRound()));
            int tokenCount = game.getRealPlayersExcludingThis(netrunner).stream()
                    .mapToInt(other -> netrunner.getDebtTokenCount(
                            other.getColor(), NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL))
                    .sum();
            if (tokenCount < 1) continue;
            MessageHelper.sendMessageToChannel(
                    netrunner.getCorrectChannel(),
                    netrunner.getRepresentation() + " gained "
                            + StringHelper.pluralize(tokenCount, "trade good") + " from **Data Mining** with "
                            + StringHelper.pluralize(tokenCount, "control token") + " on their faction sheet. "
                            + netrunner.gainTG(tokenCount, true));
        }
    }

    public static void clearDataMining(Game game, Player player) {
        if (game != null && player != null) {
            game.removeStoredValue(DATA_MINING_RESOLVED + player.getFaction());
        }
    }
}
