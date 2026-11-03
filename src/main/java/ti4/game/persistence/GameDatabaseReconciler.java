package ti4.game.persistence;

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
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.service.persistence.GameDatabaseSyncPipeline;
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

    public static boolean queueCatchUpReconciliation() {
        return CHAIN.startAfterCurrentRun(Reconciliation::new);
    }

    static final class Reconciliation implements GameDatabaseReconciliationChain.Run {

        private final List<String> discrepancies = new ArrayList<>();
        private final List<String> unloadableGameNames = new ArrayList<>();
        private final Failures failures = new Failures();
        private final Set<String> existingGameNames = new HashSet<>();
        private long startedAt;
        private Map<String, PersistedGameState> persistedStates;
        private GameEntityPersistenceService persistenceService;
        private int gameCount;
        private int revisionBackfills;

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
            try {
                reconcileOrSkip(gameName);
            } catch (Exception e) {
                failures.record(gameName, e);
            }
        }

        private void reconcileOrSkip(String gameName) {
            PersistedGameState persistedState = persistedStates.get(gameName);
            String fileRevisionKey = GameManager.readRevisionKey(gameName);
            if (persistedState != null && persistedState.matchesRevision(fileRevisionKey)) {
                existingGameNames.add(gameName);
                return;
            }
            if (wasChangedAfter(gameName, startedAt)) {
                existingGameNames.add(gameName);
                return;
            }
            long unchangedSince = startedAt;
            if (fileRevisionKey == null) {
                GameManager.backfillRevisionKey(gameName);
                unchangedSince = Math.max(startedAt, lastModified(gameName));
            }
            Game game = loadGameFile(gameName, failures);
            if (game == null) return;
            existingGameNames.add(gameName);
            if (wasChangedAfter(gameName, unchangedSince)) return;
            reconcileGame(game, persistedState, persistenceService, failures, () -> revisionBackfills++)
                    .ifPresent(discrepancies::add);
        }

        @Override
        public void finish() {
            for (String persistedGameName : persistedStates.keySet()) {
                if (existingGameNames.contains(persistedGameName)) continue;
                if (gameFileExists(persistedGameName)) {
                    unloadableGameNames.add(persistedGameName);
                    continue;
                }
                discrepancies.add(repair(
                        persistedGameName,
                        "in the database but has no game file",
                        () -> persistenceService.delete(persistedGameName),
                        failures));
            }

            Optional<String> userCleanup = reconcileUnreferencedUsers();

            reportUnloadableGames(unloadableGameNames);
            report(discrepancies, userCleanup, gameCount, revisionBackfills, failures);
        }
    }

    static final class Failures {
        private final List<String> gameNames = new ArrayList<>();
        private Exception firstCause;

        void record(String gameName, Exception cause) {
            gameNames.add(gameName);
            if (firstCause == null) firstCause = cause;
        }

        boolean isEmpty() {
            return gameNames.isEmpty();
        }

        String summary() {
            return StringUtils.abbreviate(
                    String.format(
                            "%s hit errors on %s (the stack trace is from %s): %s",
                            TASK_NAME,
                            StringHelper.pluralize(gameNames.size(), "game"),
                            gameNames.getFirst(),
                            String.join(", ", gameNames)),
                    MAX_DISCREPANCY_LENGTH);
        }
    }

    private static Optional<String> reconcileGame(
            Game game,
            PersistedGameState persistedState,
            GameEntityPersistenceService persistenceService,
            Failures failures,
            Runnable onRevisionBackfilled) {
        String gameName = game.getName();
        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);
        if (persistedState == null) {
            return Optional.of(repair(
                    gameName, "missing from the database", () -> persistenceService.restore(snapshot), failures));
        }

        PersistedGameState expectedState = PersistedGameState.of(snapshot);
        List<String> differences = expectedState.describeDifferencesFrom(persistedState);
        if (differences.isEmpty()) {
            if (!expectedState.hasSameRevisionColumnsAs(persistedState)) {
                backfillRevision(snapshot, persistenceService, failures, onRevisionBackfilled);
            }
            return Optional.empty();
        }
        return Optional.of(repair(
                gameName,
                "differs in " + String.join("; ", differences),
                () -> persistenceService.restore(snapshot),
                failures));
    }

    private static void backfillRevision(
            GameEntitySnapshot snapshot,
            GameEntityPersistenceService persistenceService,
            Failures failures,
            Runnable onRevisionBackfilled) {
        if (DatabasePersistenceGate.isDisabled()) return;
        String gameName = snapshot.game().getGameName();
        try {
            persistenceService.updateRevision(
                    gameName, snapshot.game().getRevisionKey(), snapshot.game().getMappingVersion());
            onRevisionBackfilled.run();
        } catch (Exception e) {
            failures.record(gameName, e);
        }
    }

    private static void reportUnloadableGames(List<String> gameNames) {
        if (gameNames.isEmpty()) return;
        BotLogger.critical(StringUtils.abbreviate(
                String.format(
                        "%s kept the database rows of %s whose game files could not be loaded"
                                + " (they may be corrupt): %s",
                        TASK_NAME, StringHelper.pluralize(gameNames.size(), "game"), String.join(", ", gameNames)),
                MAX_REPORT_LENGTH));
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
        return lastModified(gameName) > timestamp;
    }

    private static long lastModified(String gameName) {
        return Storage.getGameFile(gameName + Constants.TXT).lastModified();
    }

    private static boolean gameFileExists(String gameName) {
        return Storage.getGameFile(gameName + Constants.TXT).exists();
    }

    private static Game loadGameFile(String gameName, Failures failures) {
        try {
            return GameManager.loadGameFile(gameName);
        } catch (Exception e) {
            failures.record(gameName, e);
            return null;
        }
    }

    private static String describe(String gameName, String discrepancy) {
        return StringUtils.abbreviate(gameName + ": " + discrepancy, MAX_DISCREPANCY_LENGTH);
    }

    private static String repair(String gameName, String discrepancy, Runnable databaseWrite, Failures failures) {
        String description = describe(gameName, discrepancy);
        if (DatabasePersistenceGate.isDisabled()) return description + " (not repaired: database maintenance mode)";
        try {
            databaseWrite.run();
            return description;
        } catch (Exception e) {
            failures.record(gameName, e);
            return description + " (repair failed)";
        }
    }

    private static void report(
            List<String> discrepancies,
            Optional<String> userCleanup,
            int gameCount,
            int revisionBackfills,
            Failures failures) {
        if (!failures.isEmpty()) {
            BotLogger.error(failures.summary(), failures.firstCause);
        }
        if (discrepancies.isEmpty()) {
            String inSync = String.format("%s found the database in sync with all %,d games.", TASK_NAME, gameCount)
                    + describeRevisionBackfills(revisionBackfills);
            BotLogger.logCron(
                    userCleanup.map(cleanup -> inSync + "\n- " + cleanup).orElse(inSync));
            return;
        }

        StringBuilder report = new StringBuilder(String.format(
                "**%s found %s out of sync with their game files and updated the database:**",
                TASK_NAME, StringHelper.pluralize(discrepancies.size(), "game")));
        int cleanupLength = userCleanup.map(cleanup -> cleanup.length() + 3).orElse(0);
        String backfills = describeRevisionBackfills(revisionBackfills);
        int reportLimit = MAX_REPORT_LENGTH - cleanupLength - backfills.length() - 40;
        int reported = 0;
        for (String discrepancy : discrepancies) {
            if (report.length() + discrepancy.length() + 3 > reportLimit) break;
            report.append("\n- ").append(discrepancy);
            reported++;
        }
        if (reported < discrepancies.size()) {
            report.append(String.format("\n...and %,d more.", discrepancies.size() - reported));
        }
        userCleanup.ifPresent(cleanup -> report.append("\n- ").append(cleanup));
        if (!backfills.isEmpty()) report.append("\n").append(backfills.strip());
        BotLogger.error(report.toString());
    }

    private static String describeRevisionBackfills(int revisionBackfills) {
        if (revisionBackfills == 0) return "";
        return String.format(
                " Recorded the current revision key and mapping version for %s.",
                StringHelper.pluralize(revisionBackfills, "unchanged game"));
    }
}
