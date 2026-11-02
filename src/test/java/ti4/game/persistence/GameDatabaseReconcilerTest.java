package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntityPersistenceService;
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
            fixture.logs.verify(() -> BotLogger.error(contains("game file may be corrupt")));
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
            ManagedGame managedGame = mock(ManagedGame.class);
            when(managedGame.getRevisionKey()).thenReturn("revision");
            fixture.manager.when(() -> GameManager.getManagedGame("unchanged")).thenReturn(managedGame);
            when(state.matchesRevision("revision")).thenReturn(true);
            fixture.run();
            fixture.loader.verifyNoInteractions();
            fixture.storage.verifyNoInteractions();
            verifyNoInteractions(fixture.persistence);
        }
    }

    @Test
    void differingRevisionLoadsTheGame() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("changed", mock(PersistedGameState.class));
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("changed"));
            ManagedGame managedGame = mock(ManagedGame.class);
            when(managedGame.getRevisionKey()).thenReturn("new-revision");
            fixture.manager.when(() -> GameManager.getManagedGame("changed")).thenReturn(managedGame);
            when(fixture.file.exists()).thenReturn(true);
            fixture.run();
            fixture.loader.verify(() -> GameLoadService.load("changed"));
        }
    }

    @Test
    void failedManagedLookupFallsBackToReadOnlyLoadingAndContinues() {
        try (Fixture fixture = new Fixture()) {
            fixture.persisted.put("broken", mock(PersistedGameState.class));
            PersistedGameState healthy = mock(PersistedGameState.class);
            fixture.persisted.put("healthy", healthy);
            fixture.manager.when(GameManager::getGameNames).thenReturn(List.of("broken", "healthy"));
            fixture.manager
                    .when(() -> GameManager.getManagedGame("broken"))
                    .thenThrow(new IllegalStateException("unreadable game"));
            ManagedGame managedGame = mock(ManagedGame.class);
            when(managedGame.getRevisionKey()).thenReturn("revision");
            when(healthy.matchesRevision("revision")).thenReturn(true);
            fixture.manager.when(() -> GameManager.getManagedGame("healthy")).thenReturn(managedGame);
            when(fixture.file.exists()).thenReturn(true);

            fixture.run();

            fixture.loader.verify(() -> GameLoadService.load("broken"));
            fixture.loader.verify(() -> GameLoadService.load("healthy"), never());
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

        private void run() {
            var run = new GameDatabaseReconciler.Reconciliation();
            run.start().forEach(run::reconcile);
            run.finish();
        }

        @Override
        public void close() {
            logs.close();
            gate.close();
            storage.close();
            loader.close();
            manager.close();
            context.close();
        }
    }
}
