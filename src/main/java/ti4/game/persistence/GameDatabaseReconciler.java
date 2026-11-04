package ti4.game.persistence;

import static ti4.game.persistence.GamePersistenceKeys.ENDGAMEINFO;
import static ti4.game.persistence.GamePersistenceKeys.GAMEINFO;
import static ti4.game.persistence.GamePersistenceKeys.REVISION_KEY;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.StringHelper;
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
            TASK_NAME, GameDatabaseSyncPipeline::queueReconciliationTask, DatabasePersistenceGate::isDisabled);

    public static boolean queueReconciliation() {
        return CHAIN.start(Reconciliation::new);
    }

    public static boolean queueCatchUpReconciliation() {
        return CHAIN.startAfterCurrentRun(Reconciliation::new);
    }

    private static final class Reconciliation implements GameDatabaseReconciliationChain.Run {

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
            String fileRevisionKey = GameRevisionKeyFile.read(gameName);
            if (persistedState != null && persistedState.matchesRevision(fileRevisionKey)) {
                existingGameNames.add(gameName);
                return;
            }
            if (wasChangedAfter(gameName, startedAt)) {
                existingGameNames.add(gameName);
                return;
            }
            if (fileRevisionKey == null) {
                GameRevisionKeyFile.backfill(gameName);
            }
            Game game = GameLoadService.load(gameName);
            if (game == null) return;
            existingGameNames.add(gameName);
            reconcileGame(game, persistedState).ifPresent(discrepancies::add);
        }

        private Optional<String> reconcileGame(Game game, PersistedGameState persistedState) {
            String gameName = game.getName();
            if (!GameEntityMapper.shouldPersist(game)) {
                if (persistedState == null) return Optional.empty();
                return Optional.of(repair(
                        gameName,
                        "in the database but has fewer than 3 players",
                        () -> persistenceService.delete(gameName),
                        failures));
            }

            GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);
            if (persistedState == null) {
                return Optional.of(repair(
                        gameName, "missing from the database", () -> persistenceService.restore(snapshot), failures));
            }

            PersistedGameState expectedState = PersistedGameState.of(snapshot);
            List<String> differences = expectedState.describeDifferencesFrom(persistedState);
            if (differences.isEmpty()) {
                if (!expectedState.hasSameRevisionColumnsAs(persistedState)) {
                    backfillRevision(snapshot);
                }
                return Optional.empty();
            }
            return Optional.of(repair(
                    gameName,
                    "differs in " + String.join("; ", differences),
                    () -> persistenceService.restore(snapshot),
                    failures));
        }

        private void backfillRevision(GameEntitySnapshot snapshot) {
            if (DatabasePersistenceGate.isDisabled()) return;
            persistenceService.updateRevision(
                    snapshot.game().getGameName(),
                    snapshot.game().getRevisionKey(),
                    snapshot.game().getMappingVersion());
            revisionBackfills++;
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

            reportUnloadableGames(unloadableGameNames);
            report();
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
            return Storage.getGameFile(gameName + Constants.TXT).lastModified() > timestamp;
        }

        private static boolean gameFileExists(String gameName) {
            return Storage.getGameFile(gameName + Constants.TXT).exists();
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

        private void report() {
            Optional<String> userCleanup = reconcileUnreferencedUsers();
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
            String remainingGames = String.format("\n...and %,d more.", discrepancies.size());
            int reportLimit = MAX_REPORT_LENGTH - cleanupLength - backfills.length() - remainingGames.length();
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

        private static final class Failures {
            private final List<String> gameNames = new ArrayList<>();
            private Exception firstCause;

            private void record(String gameName, Exception cause) {
                gameNames.add(gameName);
                if (firstCause == null) firstCause = cause;
            }

            private boolean isEmpty() {
                return gameNames.isEmpty();
            }

            private String summary() {
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
    }

    private static final class GameRevisionKeyFile {

        private GameRevisionKeyFile() {}

        @Nullable
        private static String read(String gameName) {
            return GameFileLockManager.wrapWithReadLock(gameName, () -> readFrom(gamePath(gameName)));
        }

        // TODO: Remove once every save has a revision_key line (the first nightly reconcile after #5808 adds one to
        // each). Without it, a keyless file (e.g. restored from an undo older than #5808) is just re-read each night.
        @Deprecated(forRemoval = true)
        @Nullable
        private static String backfill(String gameName) {
            return GameFileLockManager.wrapWithWriteLock(gameName, () -> {
                Path gamePath = gamePath(gameName);
                if (!Files.exists(gamePath)) return null;
                String existingRevisionKey = readFrom(gamePath);
                if (existingRevisionKey != null) return existingRevisionKey;
                String revisionKey = UUID.randomUUID().toString();
                writeRevisionKey(gamePath, revisionKey);
                return revisionKey;
            });
        }

        @Nullable
        private static String readFrom(Path gamePath) {
            try (BufferedReader reader = Files.newBufferedReader(gamePath, StandardCharsets.UTF_8)) {
                boolean inGameInfo = false;
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (GAMEINFO.equals(line)) {
                        inGameInfo = true;
                    } else if (ENDGAMEINFO.equals(line)) {
                        return null;
                    } else if (inGameInfo && isRevisionKeyLine(line)) {
                        String revisionKey =
                                line.substring(line.indexOf(' ') + 1).trim();
                        return revisionKey.isEmpty() ? null : revisionKey;
                    }
                }
                return null;
            } catch (NoSuchFileException e) {
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read the revision key of " + gamePath, e);
            }
        }

        private static boolean isRevisionKeyLine(String line) {
            int separator = line.indexOf(' ');
            String key = separator < 0 ? line : line.substring(0, separator);
            return REVISION_KEY.equals(key);
        }

        private static void writeRevisionKey(Path gamePath, String revisionKey) {
            Path temporaryPath = null;
            try {
                List<String> lines = withRevisionKey(Files.readAllLines(gamePath, StandardCharsets.UTF_8), revisionKey);
                temporaryPath = Files.createTempFile(
                        gamePath.getParent(), gamePath.getFileName().toString(), ".tmp");
                Files.write(temporaryPath, lines, StandardCharsets.UTF_8);
                PosixFileSystemUtility.setPermissionsIfPosix(temporaryPath);
                Files.move(temporaryPath, gamePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write a revision key to " + gamePath, e);
            } finally {
                deleteIfPresent(temporaryPath);
            }
        }

        private static List<String> withRevisionKey(List<String> lines, String revisionKey) {
            int gameInfoIndex = lines.indexOf(GAMEINFO);
            if (gameInfoIndex < 0) {
                throw new IllegalStateException("Game file has no " + GAMEINFO + " section");
            }
            List<String> updatedLines = new ArrayList<>(lines);
            String revisionKeyLine = REVISION_KEY + " " + revisionKey;
            for (int i = gameInfoIndex + 1; i < updatedLines.size(); i++) {
                String line = updatedLines.get(i);
                if (ENDGAMEINFO.equals(line)) break;
                if (isRevisionKeyLine(line)) {
                    updatedLines.set(i, revisionKeyLine);
                    return updatedLines;
                }
            }
            updatedLines.add(gameInfoIndex + 1, revisionKeyLine);
            return updatedLines;
        }

        private static void deleteIfPresent(@Nullable Path path) {
            if (path == null) return;
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                BotLogger.error("Failed to delete temporary file: " + path, e);
            }
        }

        private static Path gamePath(String gameName) {
            return Storage.getGamePath(gameName + Constants.TXT);
        }
    }
}
