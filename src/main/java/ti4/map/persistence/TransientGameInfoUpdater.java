package ti4.map.persistence;

import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiscordantStarsHelper;
import ti4.map.Game;
import ti4.message.BotLogger;

class TransientGameInfoUpdater {

    public static void update(Game game) {
        try {
            ButtonHelperFactionSpecific.checkIihqAttachment(game);
            DiscordantStarsHelper.checkTombWorlds(game);
            DiscordantStarsHelper.checkGardenWorlds(game);
            DiscordantStarsHelper.checkSigil(game);
            DiscordantStarsHelper.checkSaeraMech(game);
            DiscordantStarsHelper.checkOlradinMech(game);
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Error adding transient attachment tokens for game " + game.getName(), e);
        }
    }
}
