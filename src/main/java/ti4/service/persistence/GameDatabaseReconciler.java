package ti4.service.persistence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.GameEntityPersistenceService;
import ti4.spring.service.persistence.GameEntitySnapshot;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.spring.service.persistence.PersistedGameStateService;
import ti4.spring.service.persistence.UnreferencedUserService;

@UtilityClass
public class GameDatabaseReconciler {

    private static final String TASK_NAME = "GameDatabaseReconciler";
    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 600;
    private static final int MAX_REPORT_LENGTH = 1800;
    private static final int MAX_DISCREPANCY_LENGTH = 300;

    public static void queueReconciliation() {
        GameDatabaseSyncPipeline.queueTask(
                TASK_NAME, EXECUTION_TIME_WARNING_THRESHOLD_SECONDS, GameDatabaseReconciler::reconcile);
    }

    private static void reconcile() {
        if (DatabasePersistenceGate.isDisabled()) return;
        try {
            long startedAt = System.currentTimeMillis();
            Map<String, PersistedGameState> persistedStates =
                    PersistedGameStateService.getBean().loadAll();
            GameEntityPersistenceService persistenceService = GameEntityPersistenceService.getBean();

            List<String> discrepancies = new ArrayList<>();
            Set<String> existingGameNames = new HashSet<>();
            List<ManagedGame> managedGames = GameManager.getManagedGames();
            for (ManagedGame managedGame : managedGames) {
                if (Thread.currentThread().isInterrupted()) {
                    BotLogger.warning(TASK_NAME + " was interrupted before it finished.");
                    return;
                }
                if (DatabasePersistenceGate.isDisabled()) {
                    BotLogger.warning(TASK_NAME + " stopped because database maintenance mode was turned on.");
                    return;
                }
                String gameName = managedGame.getName();
                if (wasChangedAfter(gameName, startedAt)) {
                    existingGameNames.add(gameName);
                    continue;
                }
                Game game = loadGame(managedGame);
                if (game == null) continue;
                existingGameNames.add(gameName);
                long gameFileModified = GameManager.getGameFileLastModified(gameName);
                if (gameFileModified > startedAt) continue;
                reconcileGame(game, gameFileModified, persistedStates.get(gameName), persistenceService)
                        .ifPresent(discrepancies::add);
            }
            for (String persistedGameName : persistedStates.keySet()) {
                if (existingGameNames.contains(persistedGameName)) continue;
                discrepancies.add(repair(
                        persistedGameName,
                        "in the database but has no game file",
                        () -> persistenceService.delete(persistedGameName)));
            }

            reconcileUnreferencedUsers().ifPresent(discrepancies::add);

            report(discrepancies, managedGames.size());
        } catch (Exception e) {
            BotLogger.error("**" + TASK_NAME + " failed.**", e);
        }
    }

    private static Optional<String> reconcileGame(
            Game game,
            long gameFileModified,
            PersistedGameState persistedState,
            GameEntityPersistenceService persistenceService) {
        String gameName = game.getName();
        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, gameFileModified);
        if (persistedState == null) {
            return Optional.of(
                    repair(gameName, "missing from the database", () -> persistenceService.replace(snapshot)));
        }

        List<String> differences = PersistedGameState.of(snapshot).describeDifferencesFrom(persistedState);
        if (differences.isEmpty()) return Optional.empty();
        return Optional.of(repair(
                gameName, "differs in " + String.join("; ", differences), () -> persistenceService.replace(snapshot)));
    }

    private static Optional<String> reconcileUnreferencedUsers() {
        if (DatabasePersistenceGate.isDisabled()) return Optional.empty();
        UnreferencedUserService unreferencedUserService = SpringContext.getBean(UnreferencedUserService.class);
        List<String> unreferencedUserIds = unreferencedUserService.findUnreferencedUserIds();
        if (unreferencedUserIds.isEmpty()) return Optional.empty();

        String description = StringUtils.abbreviate(
                String.format(
                        "%,d discord_user rows are not referenced by any player, title or standalone title: %s",
                        unreferencedUserIds.size(), String.join(", ", unreferencedUserIds)),
                MAX_DISCREPANCY_LENGTH);
        try {
            int deleted = unreferencedUserService.deleteUnreferencedUsers(unreferencedUserIds);
            return Optional.of(description + String.format(" (deleted %,d)", deleted));
        } catch (Exception e) {
            BotLogger.error(TASK_NAME + " could not delete unreferenced users.", e);
            return Optional.of(description + " (repair failed)");
        }
    }

    private static boolean wasChangedAfter(String gameName, long timestamp) {
        return GameManager.getGameFileLastModified(gameName) > timestamp;
    }

    private static Game loadGame(ManagedGame managedGame) {
        try {
            return managedGame.getGame();
        } catch (Exception e) {
            BotLogger.error(TASK_NAME + " could not load game " + managedGame.getName() + ".", e);
            return null;
        }
    }

    private static String repair(String gameName, String discrepancy, Runnable databaseWrite) {
        String description = StringUtils.abbreviate(gameName + ": " + discrepancy, MAX_DISCREPANCY_LENGTH);
        if (DatabasePersistenceGate.isDisabled()) return description + " (not repaired: database maintenance mode)";
        try {
            databaseWrite.run();
            return description;
        } catch (Exception e) {
            BotLogger.error(TASK_NAME + " could not repair game " + gameName + ".", e);
            return description + " (repair failed)";
        }
    }

    private static void report(List<String> discrepancies, int managedGameCount) {
        if (discrepancies.isEmpty()) {
            BotLogger.logCron(String.format(
                    "%s found the database in sync with all %,d managed games.", TASK_NAME, managedGameCount));
            return;
        }

        StringBuilder report = new StringBuilder(String.format(
                "**%s found %,d games out of sync with their game files and updated the database:**",
                TASK_NAME, discrepancies.size()));
        int reported = 0;
        for (String discrepancy : discrepancies) {
            if (report.length() + discrepancy.length() + 3 > MAX_REPORT_LENGTH) break;
            report.append("\n- ").append(discrepancy);
            reported++;
        }
        if (reported < discrepancies.size()) {
            report.append(String.format("\n...and %,d more.", discrepancies.size() - reported));
        }
        BotLogger.error(report.toString());
    }
}
