package ti4.service.persistence;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.GameEntityPersistenceService;
import ti4.spring.service.persistence.GameEntitySnapshot;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.spring.service.persistence.PersistedGameStateService;
import ti4.spring.service.persistence.UnreferencedUserService;

@UtilityClass
public class GameDatabaseReconciler {

    private static final String TASK_NAME = "GameDatabaseReconciler";
    private static final int MAX_REPORT_LENGTH = 1800;
    private static final int MAX_DISCREPANCY_LENGTH = 300;
    private static final GameDatabaseReconciliationChain CHAIN = new GameDatabaseReconciliationChain(
            TASK_NAME, GameDatabaseSyncPipeline::queueTask, DatabasePersistenceGate::isDisabled);

    public static boolean queueReconciliation() {
        return CHAIN.start(Reconciliation::new);
    }

    private static final class Reconciliation implements GameDatabaseReconciliationChain.Run {

        private final List<String> discrepancies = new ArrayList<>();
        private final Set<String> existingGameNames = new HashSet<>();
        private long startedAt;
        private Map<String, PersistedGameState> persistedStates;
        private GameEntityPersistenceService persistenceService;
        private int gameCount;

        @Override
        public List<String> start() {
            startedAt = System.currentTimeMillis();
            persistedStates = PersistedGameStateService.getBean().loadAll();
            persistenceService = GameEntityPersistenceService.getBean();
            List<String> gameNames = GameManager.getGameNames().stream()
                    .sorted(Comparator.reverseOrder())
                    .toList();
            gameCount = gameNames.size();
            return gameNames;
        }

        @Override
        public void reconcile(String gameName) {
            if (wasChangedAfter(gameName, startedAt)) {
                existingGameNames.add(gameName);
                return;
            }
            Game game = loadGame(gameName);
            if (game == null) return;
            existingGameNames.add(gameName);
            reconcileGame(game, persistedStates.get(gameName), persistenceService)
                    .ifPresent(discrepancies::add);
        }

        @Override
        public void finish() {
            for (String persistedGameName : persistedStates.keySet()) {
                if (existingGameNames.contains(persistedGameName)) continue;
                if (gameFileExists(persistedGameName)) {
                    discrepancies.add(describe(
                            persistedGameName,
                            "in the database but its game file could not be loaded"
                                    + " (not repaired: the game file may be corrupt)"));
                    continue;
                }
                discrepancies.add(repair(
                        persistedGameName,
                        "in the database but has no game file",
                        () -> persistenceService.delete(persistedGameName)));
            }

            reconcileUnreferencedUsers().ifPresent(discrepancies::add);

            report(discrepancies, gameCount);
        }
    }

    private static Optional<String> reconcileGame(
            Game game, PersistedGameState persistedState, GameEntityPersistenceService persistenceService) {
        String gameName = game.getName();
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

    private static Optional<String> reconcileUnreferencedUsers() {
        if (DatabasePersistenceGate.isDisabled()) return Optional.empty();
        UnreferencedUserService unreferencedUserService = UnreferencedUserService.getBean();
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
        return Storage.getGameFile(gameName + Constants.TXT).lastModified() > timestamp;
    }

    private static boolean gameFileExists(String gameName) {
        return Storage.getGameFile(gameName + Constants.TXT).exists();
    }

    private static Game loadGame(String gameName) {
        try {
            ManagedGame managedGame = GameManager.getManagedGame(gameName);
            return managedGame == null ? null : managedGame.getGame();
        } catch (Exception e) {
            BotLogger.error(TASK_NAME + " could not load game " + gameName + ".", e);
            return null;
        }
    }

    private static String describe(String gameName, String discrepancy) {
        return StringUtils.abbreviate(gameName + ": " + discrepancy, MAX_DISCREPANCY_LENGTH);
    }

    private static String repair(String gameName, String discrepancy, Runnable databaseWrite) {
        String description = describe(gameName, discrepancy);
        if (DatabasePersistenceGate.isDisabled()) return description + " (not repaired: database maintenance mode)";
        try {
            databaseWrite.run();
            return description;
        } catch (Exception e) {
            BotLogger.error(TASK_NAME + " could not repair game " + gameName + ".", e);
            return description + " (repair failed)";
        }
    }

    private static void report(List<String> discrepancies, int gameCount) {
        if (discrepancies.isEmpty()) {
            BotLogger.logCron(String.format("%s found the database in sync with all %,d games.", TASK_NAME, gameCount));
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
