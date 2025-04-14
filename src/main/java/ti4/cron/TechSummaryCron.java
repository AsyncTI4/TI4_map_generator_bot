package ti4.cron;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.metadata.TechSummariesMetadataManager;

@UtilityClass
public class TechSummaryCron {

    public static void register() {
        CronManager.schedulePeriodically(TechSummaryCron.class, TechSummaryCron::postTechSummaries, 5, 10, TimeUnit.MINUTES);
    }

    private static void postTechSummaries() {
        TechSummariesMetadataManager.consumeAndPersist(TechSummaryCron::postTechSummaries);
    }

    private static void postTechSummaries(TechSummariesMetadataManager.TechSummaries techSummaries) {
        if (techSummaries == null) {
            BotLogger.warning("Unable to run TechSummaryCron: TechSummary was unavailable.");
            return;
        }
        techSummaries.gameNameToTechSummary().entrySet()
            .removeIf(e -> tryToPostTechSummary(e.getKey(), e.getValue()));
    }

    private static boolean tryToPostTechSummary(String gameName, TechSummariesMetadataManager.RoundTechSummaries roundTechSummaries) {
        try {
            var managedGame = GameManager.getManagedGame(gameName);
            if (managedGame == null || managedGame.isHasEnded() ||  managedGame.getTableTalkChannel() == null
                    || managedGame.getRound() != roundTechSummaries.round()) {
                return true;
            }
            var techSummaries = roundTechSummaries.techSummaries();
            if (managedGame.getRealPlayers().size() != techSummaries.size()) {
                return false;
            }
            postTechSummary(managedGame.getGame(), techSummaries);
            return true;
        } catch (Exception e) {
            BotLogger.error("TechSummaryCron failed for game: " + gameName, e);
            return false;
        }
    }

    private static void postTechSummary(Game game, List<TechSummariesMetadataManager.FactionTechSummary> techSummaries) {
        StringBuilder msg = new StringBuilder("**__Tech Summary For Round " + game.getRound() + "__**\n");
        for (var techSummary : techSummaries) {
            Player player = game.getPlayerFromColorOrFaction(techSummary.getFaction());
            if (player == null) continue;

            msg.append(player.getFactionEmoji()).append(":");
            if (techSummary.getResearchAgreementTech() != null) {
                msg.append(" (from _Research Agreement_:");
                for (String tech : techSummary.getResearchAgreementTech()) {
                    msg.append(" ").append(Mapper.getTech(tech).getNameRepresentation());
                }
                msg.append(")");
            }
            if (techSummary.getTech() != null) {
                for (String tech : techSummary.getTech()) {
                    msg.append(" ").append(Mapper.getTech(tech).getNameRepresentation());
                }
            } else {
                msg.append(" Did not resolve **Technology** ability");
            }
            msg.append("\n");
        }

        MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), msg.toString());
    }
}
