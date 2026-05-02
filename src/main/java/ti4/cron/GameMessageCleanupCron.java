package ti4.cron;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageManager.GameMessage;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class GameMessageCleanupCron {

    private static final long TWO_WEEKS_MS = TimeUnit.DAYS.toMillis(14);

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                GameMessageCleanupCron.class, GameMessageCleanupCron::cleanup, 4, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanup() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running GameMessageCleanupCron.");
        try {
            cleanupGameMessages();
        } catch (Exception e) {
            BotLogger.error("**GameMessageCleanupCron failed.**", e);
        }
        BotLogger.logCron("Finished GameMessageCleanupCron.");
    }

    private static void cleanupGameMessages() {
        Map<String, List<GameMessage>> allMessages = GameMessageManager.getAll();

        Set<String> gameNamesToRemove = new HashSet<>();
        Map<String, Collection<String>> messageIdsToRemoveByGame = new HashMap<>();

        long twoWeeksAgo = System.currentTimeMillis() - TWO_WEEKS_MS;

        for (var entry : allMessages.entrySet()) {
            String gameName = entry.getKey();
            List<GameMessage> messages = entry.getValue();

            // Remove entries for games with empty message lists
            if (messages.isEmpty()) {
                gameNamesToRemove.add(gameName);
                continue;
            }

            ManagedGame managedGame = GameManager.getManagedGame(gameName);

            // Remove entries for games that have ended or are no longer tracked
            if (managedGame == null || managedGame.isHasEnded()) {
                gameNamesToRemove.add(gameName);
                continue;
            }

            int realPlayerCount = managedGame.getRealPlayers().size();
            List<String> idsToRemove = new ArrayList<>();

            for (GameMessage message : messages) {
                // Remove messages where all real players have reacted
                if (realPlayerCount > 0 && message.factionsThatReacted().size() >= realPlayerCount) {
                    idsToRemove.add(message.messageId());
                    continue;
                }
                // Remove messages with a gameSaveTime older than 2 weeks
                if (message.gameSaveTime() < twoWeeksAgo) {
                    idsToRemove.add(message.messageId());
                }
            }

            if (!idsToRemove.isEmpty()) {
                messageIdsToRemoveByGame.put(gameName, idsToRemove);
            }
        }

        int removedGames = gameNamesToRemove.size();
        int removedMessages = messageIdsToRemoveByGame.values().stream()
                .mapToInt(Collection::size)
                .sum();

        GameMessageManager.removeGameNamesAndMessages(gameNamesToRemove, messageIdsToRemoveByGame);

        BotLogger.info(String.format(
                "GameMessageCleanupCron: Removed `%d` game entries and `%d` individual messages.",
                removedGames, removedMessages));
    }
}
