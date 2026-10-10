package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.service.persistence.GameDatabaseSyncPipeline;
import ti4.spring.service.persistence.PersistedManagedGame;
import ti4.testUtils.BaseTi4Test;

class GameManagerDatabaseSyncTest extends BaseTi4Test {

    @Test
    void saveQueuesSyncAndDeleteQueuesDelete() {
        Game game = newGame("db-sync-save");

        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameSaveService.when(() -> GameSaveService.save(game, "test save")).thenReturn(true);
            gameSaveService.when(() -> GameSaveService.delete(game.getName())).thenReturn(true);
            AtomicBoolean syncQueuedUnderWriteLock = recordWriteLockWhenSyncQueued(pipeline, game);
            AtomicBoolean deleteQueuedUnderWriteLock = new AtomicBoolean();
            pipeline.when(() -> GameDatabaseSyncPipeline.queueDelete(game.getName()))
                    .thenAnswer(_ -> {
                        deleteQueuedUnderWriteLock.set(
                                GameFileLockManager.isWriteLockedByCurrentThread(game.getName()));
                        return null;
                    });

            GameManager.save(game, "test save");
            GameManager.delete(game.getName());

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(game));
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueDelete(game.getName()));
            pipeline.verifyNoMoreInteractions();
            // Queuing while the game file is still locked keeps database writes in the same order as file writes.
            assertThat(syncQueuedUnderWriteLock).isTrue();
            assertThat(deleteQueuedUnderWriteLock).isTrue();
        }
    }

    @Test
    void failedSaveDoesNotQueueSync() {
        Game game = newGame("db-sync-failed-save");

        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameSaveService.when(() -> GameSaveService.save(game, "test save")).thenReturn(false);

            try {
                GameManager.save(game, "test save");
            } catch (RuntimeException expected) {
                // GameManager.save throws when the file write fails; the database must not be touched.
            }

            pipeline.verifyNoInteractions();
        }
    }

    @Test
    void undoQueuesSyncOfTheRestoredGame() {
        Game sourceGame = newGame("db-sync-undo");
        Game undoneGame = newGame("db-sync-undo");
        undoneGame.setName(sourceGame.getName());

        try (MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameUndoService.when(() -> GameUndoService.undo(sourceGame)).thenReturn(undoneGame);
            AtomicBoolean syncQueuedUnderWriteLock = recordWriteLockWhenSyncQueued(pipeline, undoneGame);

            assertThat(GameManager.undo(sourceGame)).isSameAs(undoneGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(undoneGame));
            pipeline.verifyNoMoreInteractions();
            assertThat(syncQueuedUnderWriteLock).isTrue();
        }
    }

    @Test
    void reloadQueuesSyncOfTheLoadedGame() {
        Game reloadedGame = newGame("db-sync-reload");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.load(reloadedGame.getName()))
                    .thenReturn(reloadedGame);
            AtomicBoolean syncQueuedUnderWriteLock = recordWriteLockWhenSyncQueued(pipeline, reloadedGame);

            assertThat(GameManager.reload(reloadedGame.getName())).isSameAs(reloadedGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(reloadedGame));
            pipeline.verifyNoMoreInteractions();
            assertThat(syncQueuedUnderWriteLock).isTrue();
        }
    }

    @Test
    void reloadOfGameRecoveredFromUndoQueuesSyncOfTheRecoveredGame() {
        Game recoveredGame = newGame("db-sync-recovered");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.load(recoveredGame.getName()))
                    .thenReturn(null);
            gameUndoService
                    .when(() -> GameUndoService.loadUndoForMissingGame(recoveredGame.getName()))
                    .thenReturn(recoveredGame);

            assertThat(GameManager.reload(recoveredGame.getName())).isSameAs(recoveredGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(recoveredGame));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void reloadOfGameWithNoFileOrUndoQueuesDelete() {
        String missingGameName = uniqueName("db-sync-missing");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService.when(() -> GameLoadService.load(missingGameName)).thenReturn(null);
            gameUndoService
                    .when(() -> GameUndoService.loadUndoForMissingGame(missingGameName))
                    .thenReturn(null);

            assertThat(GameManager.reload(missingGameName)).isNull();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueDelete(missingGameName));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void gameThatFailsToLoadQueuesDelete() {
        String unloadableGameName = uniqueName("db-sync-unloadable");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService.when(() -> GameLoadService.load(unloadableGameName)).thenReturn(null);

            assertThat(GameManager.get(unloadableGameName)).isNull();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueDelete(unloadableGameName));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void warmupBuildsManagedGameFromDatabaseRowThatMatchesTheGameFile() {
        String gameName = uniqueName("warmup-current");
        String userId = uniqueName("warmup-current-user");
        PersistedManagedGame persistedGame = new PersistedManagedGame(managedGameState(gameName, userId), 1000);

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.getGameFileLastModified(gameName))
                    .thenReturn(1000L);

            assertThat(GameManager.warmupManagedGame(gameName, Map.of(gameName, persistedGame)))
                    .isTrue();

            gameLoadService.verify(() -> GameLoadService.load(gameName), never());
            pipeline.verifyNoInteractions();
        }
        ManagedGame managedGame = onlyGameOf(userId);
        assertThat(managedGame.getName()).isEqualTo(gameName);
        assertThat(managedGame.isFowMode()).isTrue();
        assertThat(managedGame.getRound()).isEqualTo(3);
        assertThat(managedGame.getRealPlayers())
                .extracting(ManagedPlayer::getId)
                .containsExactly(userId);
        assertThat(GameManager.getManagedPlayer(userId).getName()).isEqualTo("Warmup User");
    }

    @Test
    void warmupLoadsGameFileAndQueuesSyncWhenTheDatabaseRowIsStale() {
        Game game = newGame("warmup-stale");
        String userId = game.getPlayers().keySet().iterator().next();
        PersistedManagedGame staleRow = new PersistedManagedGame(managedGameState(game.getName(), userId), 1000);

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.getGameFileLastModified(game.getName()))
                    .thenReturn(2000L);
            gameLoadService.when(() -> GameLoadService.load(game.getName())).thenReturn(game);

            assertThat(GameManager.warmupManagedGame(game.getName(), Map.of(game.getName(), staleRow)))
                    .isFalse();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(game));
            pipeline.verifyNoMoreInteractions();
        }
        // The stale row said fog of war; the game file did not, and the file wins.
        assertThat(onlyGameOf(userId).isFowMode()).isFalse();
    }

    @Test
    void warmupLoadsGameFileAndQueuesSyncWhenTheGameHasNoDatabaseRow() {
        Game game = newGame("warmup-missing-row");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService.when(() -> GameLoadService.load(game.getName())).thenReturn(game);

            assertThat(GameManager.warmupManagedGame(game.getName(), Map.of())).isFalse();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(game));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void warmupWithoutTheDatabaseLoadsGameFileWithoutQueuingSync() {
        Game game = newGame("warmup-no-database");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService.when(() -> GameLoadService.load(game.getName())).thenReturn(game);

            assertThat(GameManager.warmupManagedGame(game.getName(), null)).isFalse();

            // With the database unavailable every sync would fail, so warmup must not flood the queue.
            pipeline.verifyNoInteractions();
        }
    }

    private static AtomicBoolean recordWriteLockWhenSyncQueued(
            MockedStatic<GameDatabaseSyncPipeline> pipeline, Game game) {
        AtomicBoolean queuedUnderWriteLock = new AtomicBoolean();
        pipeline.when(() -> GameDatabaseSyncPipeline.queueSync(game)).thenAnswer(_ -> {
            queuedUnderWriteLock.set(GameFileLockManager.isWriteLockedByCurrentThread(game.getName()));
            return null;
        });
        return queuedUnderWriteLock;
    }

    private static ManagedGame onlyGameOf(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        assertThat(managedPlayer).isNotNull();
        return managedPlayer.getGames().iterator().next();
    }

    private static ManagedGameState managedGameState(String gameName, String userId) {
        return new ManagedGameState(
                gameName,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                null,
                0,
                0,
                3,
                null,
                null,
                null,
                null,
                List.of(new ManagedGameState.Participant(userId, "Warmup User", true)));
    }

    private static Game newGame(String prefix) {
        Game game = new Game();
        game.setName(uniqueName(prefix));
        game.setLastModifiedDate(0);
        game.addPlayer(uniqueName(prefix + "-user"), "Test User");
        return game;
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
