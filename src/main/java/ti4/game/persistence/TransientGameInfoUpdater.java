package ti4.game.persistence;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor.VeylorUnitHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.thundersedge.TeHelperGeneral;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;

@UtilityClass
class TransientGameInfoUpdater {

    static void update(Game game) {
        try {
            ButtonHelperFactionSpecific.checkIihqAttachment(game);
            DiscordantStarsHelper.checkGardenWorlds(game);
            DiscordantStarsHelper.checkTFTerraform(game);
            DiscordantStarsHelper.checkSigil(game);
            DiscordantStarsHelper.checkOlradinMech(game);
            VeylorUnitHandler.checkVeylorMech(game);
            DiscordantStarsHelper.checkUltimateAuthority(game);
            TeHelperGeneral.checkTransientInfo(game);
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(game), "Error adding transient attachment tokens for game " + game.getName(), e);
        }
    }
}
