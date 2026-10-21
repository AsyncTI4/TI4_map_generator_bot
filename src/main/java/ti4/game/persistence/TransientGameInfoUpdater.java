package ti4.game.persistence;

import java.util.ArrayList;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.verydith.VerydithLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.veylor.VeylorUnitHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.bluereverie.BlueReverieTechHelper;
import ti4.helpers.thundersedge.TeHelperGeneral;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.service.game.MonumentsService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class TransientGameInfoUpdater {

    static void update(Game game) {
        try {
            removePlanetsMissingFromTheGame(game);
            ButtonHelperFactionSpecific.checkIihqAttachment(game);
            DiscordantStarsHelper.checkGardenWorlds(game);
            DiscordantStarsHelper.checkTFTerraform(game);
            DiscordantStarsHelper.checkBRTaranisCrest(game);
            DiscordantStarsHelper.checkSigil(game);
            DiscordantStarsHelper.checkOlradinMech(game);
            VeylorUnitHandler.checkVeylorMech(game);
            VerydithLeadersHandler.checkVerydithCommander(game);
            DiscordantStarsHelper.checkUltimateAuthority(game);
            MonumentsService.syncZelianAsteroidFieldToken(game);
            TeHelperGeneral.checkTransientInfo(game);
            BlueReverieTechHelper.syncEcologicalEngineeringAttachments(game);
            for (Player player : game.getRealPlayers()) {
                CommanderUnlockCheckService.checkPlayer(player, "ta");
                MonumentsBRButtonHandler.checkMonumentHonorDishonorFlip(game, player);
                MonumentsBRButtonHandler.checkDishonorMonumentCondition(game, player);
            }
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(game), "Error adding transient attachment tokens for game " + game.getName(), e);
        }
    }

    private static void removePlanetsMissingFromTheGame(Game game) {
        Set<String> knownPlanets = game.getPlanetsInfo().keySet();
        for (Player player : game.getPlayers().values()) {
            for (String planet : new ArrayList<>(player.getPlanets())) {
                if (!knownPlanets.contains(planet)) {
                    player.removePlanet(planet);
                    BotLogger.warning(
                            new LogOrigin(player),
                            "Planet " + planet + " not found in game " + game.getName()
                                    + ". Removed planet from player.");
                }
            }
        }
    }
}
