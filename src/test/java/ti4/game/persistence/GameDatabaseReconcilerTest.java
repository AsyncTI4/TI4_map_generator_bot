package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.GameEntityPersistenceService;
import ti4.spring.service.persistence.GameEntitySnapshot;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.spring.service.persistence.PersistedGameStateService;
import ti4.spring.service.persistence.UnreferencedUserService;

class GameDatabaseReconcilerTest {
    @TempDir
    static Path temporaryDirectory;

    @Test
    void unreadableFileKeepsItsDatabaseRows() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("broken", mock(PersistedGameState.class));
            when(fixture.file.exists()).thenReturn(true);
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("broken"));

            fixture.run();

            verifyNoInteractions(fixture.persistence);
            // Kept corrupt games get one critical report of their own, not a place in the "out of sync" list.
            fixture.logs.verify(() -> BotLogger.critical(contains("1 game whose game files could not be loaded")));
            fixture.logs.verify(() -> BotLogger.logCron(contains("in sync with all 1 games")));
            fixture.logs.verify(() -> BotLogger.error(anyString()), never());
        }
    }

    @Test
    void rowWithMatchingContentButStaleRevisionIsBackfilledQuietly() {
        try (Fixture fixture = new Fixture()) {
            GameEntitySnapshot current = snapshot("legacy", "revision", GameEntityMapper.MAPPING_VERSION);
            fixture.persisted.put("legacy", PersistedGameState.of(snapshot("legacy", null, null)));
            fixture.loadsSnapshot("legacy", current);

            fixture.run();

            verify(fixture.persistence).updateRevision("legacy", "revision", GameEntityMapper.MAPPING_VERSION);
            verify(fixture.persistence, never()).restore(any());
            verify(fixture.persistence, never()).replace(any());
            fixture.logs.verify(() -> BotLogger.logCron(argThat(message ->
                    message.contains("in sync with all 1 games") && message.contains("for 1 unchanged game"))));
            fixture.logs.verify(() -> BotLogger.error(anyString()), never());
        }
    }

    @Test
    void differingContentIsRestoredWithoutOverwritingUserNames() {
        try (Fixture fixture = new Fixture()) {
            GameEntitySnapshot current = snapshot("changed", "revision", GameEntityMapper.MAPPING_VERSION);
            current.game().setRound(5);
            fixture.persisted.put("changed", PersistedGameState.of(snapshot("changed", null, null)));
            fixture.loadsSnapshot("changed", current);

            fixture.run();

            verify(fixture.persistence).restore(current);
            verify(fixture.persistence, never()).replace(any());
            verify(fixture.persistence, never()).updateRevision(anyString(), anyString(), any());
            // Only real content differences are listed; the stale revision columns are not.
            fixture.logs.verify(() -> BotLogger.error(argThat(message -> message.contains("round (expected 5, found 1)")
                    && !message.contains("revisionKey")
                    && !message.contains("mappingVersion"))));
        }
    }

    private static GameEntitySnapshot snapshot(String gameName, String revisionKey, Integer mappingVersion) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setRound(1);
        game.setRevisionKey(revisionKey);
        game.setMappingVersion(mappingVersion);
        return new GameEntitySnapshot(game, List.of(), List.of());
    }

    @Test
    void failedRepairsAreReportedInOneMessageInsteadOfOnePerGame() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("deleted-1", mock(PersistedGameState.class));
            fixture.persisted.put("deleted-2", mock(PersistedGameState.class));
            fixture.persisted.put("deleted-3", mock(PersistedGameState.class));
            doThrow(new IllegalStateException("database down"))
                    .when(fixture.persistence)
                    .delete(anyString());

            fixture.run();

            fixture.logs.verify(
                    () -> BotLogger.error(contains("hit errors on 3 games"), any(Throwable.class)), times(1));
            fixture.logs.verify(() -> BotLogger.error(contains("repair failed")), times(1));
            fixture.logs.verifyNoMoreInteractions();
        }
    }

    @Test
    void oneFailingGameIsReportedWithoutStoppingTheRun() {
        try (Fixture fixture = new Fixture()) {
            PersistedGameState failing = mock(PersistedGameState.class);
            when(failing.matchesRevision(anyString())).thenThrow(new IllegalStateException("mapping bug"));
            fixture.persisted.put("failing", failing);
            fixture.persisted.put("deleted", mock(PersistedGameState.class));
            fixture.revisionFile("failing", "revision");
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("failing"));

            fixture.run();

            // The run still reaches finish(): the orphaned row is deleted and the failure is reported once.
            verify(fixture.persistence).delete("deleted");
            fixture.logs.verify(
                    () -> BotLogger.error(contains("hit errors on 1 game"), any(Throwable.class)), times(1));
        }
    }

    @Test
    void gameFileWithoutRevisionKeyIsReconciledWithoutWritingOne() {
        try (Fixture fixture = new Fixture()) {
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("legacy", "keyed"));
            fixture.revisionFile("legacy", null);
            fixture.revisionFile("keyed", "revision");
            when(fixture.file.exists()).thenReturn(true);

            fixture.run();

            assertThat(fixture.revisionLines("legacy")).noneMatch(line -> line.startsWith("revision_key"));
            assertThat(fixture.revisionLines("keyed")).contains("revision_key revision");
        }
    }

    @Test
    void missingFileDeletesItsOrphanedRows() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("deleted", mock(PersistedGameState.class));

            fixture.run();

            verify(fixture.persistence).delete("deleted");
        }
    }

    @Test
    void gameChangedDuringRunIsNotRepaired() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("changed", mock(PersistedGameState.class));
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("changed"));
            when(fixture.file.lastModified()).thenReturn(Long.MAX_VALUE);

            fixture.run();

            verifyNoInteractions(fixture.persistence);
            fixture.loader.verify(() -> GameLoadService.load("changed"), never());
        }
    }

    @Test
    void cleanupIsReportedWithoutInflatingTheGameCount() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.users.findUnreferencedUserIds()).thenReturn(List.of("user"));
            when(fixture.users.deleteUnreferencedUsers(List.of("user"))).thenReturn(1);

            fixture.run();

            fixture.logs.verify(() -> BotLogger.logCron(
                    argThat(message -> message.contains("in sync with all 0 games") && message.contains("deleted 1"))));
        }
    }

    @Test
    void longReportRetainsCleanupWithinDiscordLimit() {
        try (Fixture fixture = new Fixture()) {
            IntStream.range(0, 30).forEach(i -> fixture.persisted.put("missing" + i, mock(PersistedGameState.class)));
            List<String> users =
                    IntStream.range(0, 50).mapToObj(i -> "123456789012345" + i).toList();
            when(fixture.users.findUnreferencedUserIds()).thenReturn(users);
            when(fixture.users.deleteUnreferencedUsers(users)).thenReturn(50);

            fixture.run();

            fixture.logs.verify(() -> BotLogger.error(argThat(message -> {
                assertThat(message).hasSizeLessThanOrEqualTo(1800);
                assertThat(message).contains("and ", "more.", "deleted 50");
                return true;
            })));
        }
    }

    @Test
    void matchingRevisionSkipsParsingAndDatabaseWrites() {
        try (Fixture fixture = new Fixture()) {
            PersistedGameState state = mock(PersistedGameState.class);
            fixture.persisted.put("unchanged", state);
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("unchanged"));
            fixture.revisionFile("unchanged", "revision");
            when(state.matchesRevision("revision")).thenReturn(true);
            fixture.run();
            fixture.loader.verifyNoInteractions();
            fixture.storage.verify(() -> Storage.getGameFile(anyString()), never());
            verifyNoInteractions(fixture.persistence);
        }
    }

    @Test
    void differingRevisionLoadsTheGame() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("changed", mock(PersistedGameState.class));
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("changed"));
            fixture.revisionFile("changed", "new-revision");
            when(fixture.file.exists()).thenReturn(true);
            fixture.run();
            fixture.loader.verify(() -> GameLoadService.load("changed"));
        }
    }

    @Test
    void gameNotLoadedInMemoryFallsBackToReadOnlyLoadingAndContinues() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("broken", mock(PersistedGameState.class));
            PersistedGameState healthy = mock(PersistedGameState.class);
            fixture.persisted.put("healthy", healthy);
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("broken", "healthy"));
            when(healthy.matchesRevision("revision")).thenReturn(true);
            fixture.revisionFile("healthy", "revision");
            when(fixture.file.exists()).thenReturn(true);

            fixture.run();

            fixture.loader.verify(() -> GameLoadService.load("broken"));
            fixture.loader.verify(() -> GameLoadService.load("healthy"), never());
            verifyNoInteractions(fixture.persistence);
        }
    }

    @Test
    void loadFailureIsReportedOnceAndLaterGamesAreReconciled() {
        try (Fixture fixture = new Fixture()) {
            GameEntitySnapshot current = snapshot("healthy", "revision", GameEntityMapper.MAPPING_VERSION);
            fixture.loadsSnapshot("healthy", current);
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("z-broken", "healthy"));
            IllegalStateException failure = new IllegalStateException("load failed");
            fixture.loader.when(() -> GameLoadService.load("z-broken")).thenThrow(failure);

            fixture.run();

            verify(fixture.persistence).restore(current);
            fixture.logs.verify(() -> BotLogger.error(contains("hit errors on 1 game"), same(failure)), times(1));
        }
    }

    @Test
    void revisionUpdateFailureIsReportedWithoutCountingABackfill() {
        try (Fixture fixture = new Fixture()) {
            GameEntitySnapshot current = snapshot("legacy", "revision", GameEntityMapper.MAPPING_VERSION);
            fixture.persisted.put("legacy", PersistedGameState.of(snapshot("legacy", null, null)));
            fixture.loadsSnapshot("legacy", current);
            IllegalStateException failure = new IllegalStateException("update failed");
            doThrow(failure)
                    .when(fixture.persistence)
                    .updateRevision("legacy", "revision", GameEntityMapper.MAPPING_VERSION);

            fixture.run();

            fixture.logs.verify(() -> BotLogger.error(contains("hit errors on 1 game"), same(failure)), times(1));
            fixture.logs.verify(() -> BotLogger.logCron(contains("Recorded the current revision")), never());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final MockedStatic<SpringContext> context = mockStatic(SpringContext.class);
        private final MockedStatic<GameManager> manager = mockStatic(GameManager.class);
        private final MockedStatic<GameLoadService> loader = mockStatic(GameLoadService.class);
        private final MockedStatic<Storage> storage = mockStatic(Storage.class);
        private final MockedStatic<DatabasePersistenceGate> gate = mockStatic(DatabasePersistenceGate.class);
        private final MockedStatic<BotLogger> logs = mockStatic(BotLogger.class);
        private final MockedStatic<GameEntityMapper> mapper = mockStatic(GameEntityMapper.class);
        private final GameEntityPersistenceService persistence = mock(GameEntityPersistenceService.class);
        private final PersistedGameStateService states = mock(PersistedGameStateService.class);
        private final UnreferencedUserService users = mock(UnreferencedUserService.class);
        private final Map<String, PersistedGameState> persisted = new LinkedHashMap<>();
        private final File file = mock(File.class);

        private final Path directory = newDirectory();

        private Fixture() {
            storage.when(() -> Storage.getGamePath(anyString()))
                    .thenAnswer(invocation -> directory.resolve((String) invocation.getArgument(0)));
            context.when(() -> SpringContext.getBean(GameEntityPersistenceService.class))
                    .thenReturn(persistence);
            context.when(() -> SpringContext.getBean(PersistedGameStateService.class))
                    .thenReturn(states);
            context.when(() -> SpringContext.getBean(UnreferencedUserService.class))
                    .thenReturn(users);
            storage.when(() -> Storage.getGameFile(anyString())).thenReturn(file);
            when(states.loadAll()).thenReturn(persisted);
        }

        private void loadsSnapshot(String gameName, GameEntitySnapshot snapshot) {
            Game game = mock(Game.class);
            when(game.getName()).thenReturn(gameName);
            when(file.exists()).thenReturn(true);
            manager.when(GameManager::getGameNames).thenReturn(List.of(gameName));
            revisionFile(gameName, snapshot.game().getRevisionKey());
            loader.when(() -> GameLoadService.load(gameName)).thenReturn(game);
            mapper.when(() -> GameEntityMapper.toSnapshot(game)).thenReturn(snapshot);
        }

        private static Path newDirectory() {
            try {
                return Files.createTempDirectory(temporaryDirectory, "reconcile-");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void revisionFile(String gameName, String revision) {
            try {
                Files.write(
                        directory.resolve(gameName + ".txt"),
                        revision == null
                                ? List.of(GamePersistenceKeys.GAMEINFO, GamePersistenceKeys.ENDGAMEINFO)
                                : List.of(
                                        GamePersistenceKeys.GAMEINFO,
                                        GamePersistenceKeys.REVISION_KEY + " " + revision,
                                        GamePersistenceKeys.ENDGAMEINFO));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private List<String> revisionLines(String gameName) {
            try {
                return Files.readAllLines(directory.resolve(gameName + ".txt"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void run() {
            Deque<Runnable> tasks = new ArrayDeque<>();
            try (MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
                pipeline.when(() -> GameDatabaseSyncPipeline.queueReconciliationTask(any()))
                        .thenAnswer(invocation -> {
                            tasks.add(invocation.getArgument(0));
                            return true;
                        });
                assertThat(GameDatabaseReconciler.queueReconciliation()).isTrue();
                while (!tasks.isEmpty()) {
                    tasks.removeFirst().run();
                }
            }
        }

        @Override
        public void close() {
            mapper.close();
            logs.close();
            gate.close();
            storage.close();
            loader.close();
            manager.close();
            context.close();
        }
    }
}
