package ti4.game.persistence;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith.VerydithLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor.VeylorUnitHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.thundersedge.TeHelperGeneral;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class TransientGameInfoUpdater {

    static void update(Game game) {
        try {
            ButtonHelperFactionSpecific.checkIihqAttachment(game);
            DiscordantStarsHelper.checkGardenWorlds(game);
            DiscordantStarsHelper.checkTFTerraform(game);
            DiscordantStarsHelper.checkBRTaranisCrest(game);
            DiscordantStarsHelper.checkSigil(game);
            DiscordantStarsHelper.checkOlradinMech(game);
            VeylorUnitHandler.checkVeylorMech(game);
            VerydithLeadersHandler.checkVerydithCommander(game);
            DiscordantStarsHelper.checkUltimateAuthority(game);
            TeHelperGeneral.checkTransientInfo(game);
            for (Player player : game.getRealPlayers()) {
                CommanderUnlockCheckService.checkPlayer(player, "ta");
            }
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(game), "Error adding transient attachment tokens for game " + game.getName(), e);
        }
    }
}
