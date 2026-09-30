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
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.GameEntityPersistenceService;
import ti4.spring.service.persistence.GameEntitySnapshot;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.spring.service.persistence.PersistedGameStateService;

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
                    SpringContext.getBean(PersistedGameStateService.class).loadAll();
            GameEntityPersistenceService persistenceService = SpringContext.getBean(GameEntityPersistenceService.class);

            List<String> discrepancies = new ArrayList<>();
            Set<String> managedGameNames = new HashSet<>();
            List<ManagedGame> managedGames = GameManager.getManagedGames();
            for (ManagedGame managedGame : managedGames) {
                if (Thread.currentThread().isInterrupted()) {
                    BotLogger.warning(TASK_NAME + " was interrupted before it finished.");
                    return;
                }
                managedGameNames.add(managedGame.getName());
                reconcileGame(managedGame, persistedStates.get(managedGame.getName()), startedAt, persistenceService)
                        .ifPresent(discrepancies::add);
            }
            for (String persistedGameName : persistedStates.keySet()) {
                if (managedGameNames.contains(persistedGameName)) continue;
                discrepancies.add(repair(
                        persistedGameName,
                        "in the database but has no game file",
                        () -> persistenceService.delete(persistedGameName)));
            }

            report(discrepancies, managedGames.size());
        } catch (Exception e) {
            BotLogger.error("**" + TASK_NAME + " failed.**", e);
        }
    }

    private static Optional<String> reconcileGame(
            ManagedGame managedGame,
            PersistedGameState persistedState,
            long startedAt,
            GameEntityPersistenceService persistenceService) {
        String gameName = managedGame.getName();
        if (wasChangedAfter(gameName, startedAt)) return Optional.empty();

        Game game = loadGame(managedGame);
        if (game == null) return Optional.empty();

        if (!GameEntityMapper.shouldPersist(game)) {
            if (persistedState == null) return Optional.empty();
            return Optional.of(repair(
                    gameName,
                    "in the database but has fewer than 3 players",
                    () -> persistenceService.delete(gameName)));
        }

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);
        if (persistedState == null) {
            return Optional.of(
                    repair(gameName, "missing from the database", () -> persistenceService.replace(snapshot)));
        }

        List<String> differences = PersistedGameState.of(snapshot).describeDifferencesFrom(persistedState);
        if (differences.isEmpty()) return Optional.empty();
        return Optional.of(repair(
                gameName, "differs in " + String.join("; ", differences), () -> persistenceService.replace(snapshot)));
    }

    private static boolean wasChangedAfter(String gameName, long timestamp) {
        return Storage.getGameFile(gameName + Constants.TXT).lastModified() > timestamp;
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
