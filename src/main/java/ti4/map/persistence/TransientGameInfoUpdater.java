package ti4.map.persistence;

import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiscordantStarsHelper;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;

@UtilityClass
class TransientGameInfoUpdater {

    static void update(Game game) {
        try {
            ButtonHelperFactionSpecific.checkIihqAttachment(game);
            DiscordantStarsHelper.checkTombWorlds(game);
            DiscordantStarsHelper.checkGardenWorlds(game);
            DiscordantStarsHelper.checkSigil(game);
            DiscordantStarsHelper.checkSaeraMech(game);
            DiscordantStarsHelper.checkOlradinMech(game);
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(game), "Error adding transient attachment tokens for game " + game.getName(), e);
        }
    }
}
