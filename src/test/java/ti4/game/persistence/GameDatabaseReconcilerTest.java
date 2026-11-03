package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.GameEntityPersistenceService;
import ti4.spring.service.persistence.GameEntitySnapshot;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.spring.service.persistence.PersistedGameStateService;
import ti4.spring.service.persistence.UnreferencedUserService;

class GameDatabaseReconcilerTest {

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
    void legacyFileIsReconciledInTheSameRunThatBackfillsItsKey() {
        try (Fixture fixture = new Fixture()) {
            GameEntitySnapshot current = snapshot("legacy", "revision", GameEntityMapper.MAPPING_VERSION);
            fixture.persisted.put("legacy", PersistedGameState.of(snapshot("legacy", null, null)));
            fixture.loadsSnapshot("legacy", current);
            fixture.manager.when(() -> GameManager.readRevisionKey("legacy")).thenReturn(null);
            // Writing the key line moves the file's modification time past the start of the run.
            AtomicLong lastModified = new AtomicLong();
            when(fixture.file.lastModified()).thenAnswer(_ -> lastModified.get());
            fixture.manager
                    .when(() -> GameManager.backfillRevisionKey("legacy"))
                    .thenAnswer(_ -> {
                        lastModified.set(System.currentTimeMillis() + 60_000);
                        return "revision";
                    });

            fixture.run();

            // The reconciler's own key write is not a concurrent save, so the row is brought up to date now.
            verify(fixture.persistence).updateRevision("legacy", "revision", GameEntityMapper.MAPPING_VERSION);
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
            fixture.manager.when(() -> GameManager.readRevisionKey("failing")).thenReturn("revision");
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("failing"));

            fixture.run();

            // The run still reaches finish(): the orphaned row is deleted and the failure is reported once.
            verify(fixture.persistence).delete("deleted");
            fixture.logs.verify(
                    () -> BotLogger.error(contains("hit errors on 1 game"), any(Throwable.class)), times(1));
        }
    }

    @Test
    void gameFileWithoutRevisionKeyGetsOneBeforeItIsReconciled() {
        try (Fixture fixture = new Fixture()) {
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("legacy", "keyed"));
            fixture.manager.when(() -> GameManager.readRevisionKey("keyed")).thenReturn("revision");
            when(fixture.file.exists()).thenReturn(true);

            fixture.run();

            fixture.manager.verify(() -> GameManager.backfillRevisionKey("legacy"));
            fixture.manager.verify(() -> GameManager.backfillRevisionKey("keyed"), never());
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
            fixture.manager.verify(() -> GameManager.loadGameFile("changed"), never());
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
            fixture.manager.when(() -> GameManager.readRevisionKey("unchanged")).thenReturn("revision");
            when(state.matchesRevision("revision")).thenReturn(true);
            fixture.run();
            fixture.loader.verifyNoInteractions();
            fixture.manager.verify(() -> GameManager.loadGameFile("unchanged"), never());
            verifyNoInteractions(fixture.persistence);
        }
    }

    @Test
    void differingRevisionLoadsTheGame() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("changed", mock(PersistedGameState.class));
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("changed"));
            fixture.manager.when(() -> GameManager.readRevisionKey("changed")).thenReturn("new-revision");
            when(fixture.file.exists()).thenReturn(true);
            fixture.run();
            fixture.manager.verify(() -> GameManager.loadGameFile("changed"));
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
            fixture.manager.when(() -> GameManager.readRevisionKey("healthy")).thenReturn("revision");
            when(fixture.file.exists()).thenReturn(true);

            fixture.run();

            fixture.manager.verify(() -> GameManager.loadGameFile("broken"));
            fixture.manager.verify(() -> GameManager.loadGameFile("healthy"), never());
            verifyNoInteractions(fixture.persistence);
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

        private Fixture() {
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
            manager.when(() -> GameManager.readRevisionKey(gameName))
                    .thenReturn(snapshot.game().getRevisionKey());
            manager.when(() -> GameManager.loadGameFile(gameName)).thenReturn(game);
            mapper.when(() -> GameEntityMapper.toSnapshot(game)).thenReturn(snapshot);
        }

        private void run() {
            var run = new GameDatabaseReconciler.Reconciliation();
            run.start().forEach(run::reconcile);
            run.finish();
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
