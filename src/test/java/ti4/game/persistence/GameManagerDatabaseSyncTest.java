package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.HashMap;
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

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(game), any()));
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

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(undoneGame), any()));
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
                    .when(() -> GameLoadService.tryLoad(reloadedGame.getName()))
                    .thenReturn(new GameLoadResult.Loaded(reloadedGame));
            AtomicBoolean syncQueuedUnderWriteLock = recordWriteLockWhenSyncQueued(pipeline, reloadedGame);

            assertThat(GameManager.reload(reloadedGame.getName())).isSameAs(reloadedGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(reloadedGame), any()));
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
                    .when(() -> GameLoadService.tryLoad(recoveredGame.getName()))
                    .thenReturn(corruptResult());
            gameUndoService
                    .when(() -> GameUndoService.loadUndoForMissingGame(recoveredGame.getName()))
                    .thenReturn(recoveredGame);

            assertThat(GameManager.reload(recoveredGame.getName())).isSameAs(recoveredGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(recoveredGame), any()));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void reloadOfGameWithNoFileOrUndoQueuesDelete() {
        String missingGameName = uniqueName("db-sync-missing");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.tryLoad(missingGameName))
                    .thenReturn(new GameLoadResult.Missing());
            gameUndoService
                    .when(() -> GameUndoService.loadUndoForMissingGame(missingGameName))
                    .thenReturn(null);

            assertThat(GameManager.reload(missingGameName)).isNull();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueDelete(missingGameName));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void reloadOfCorruptGameWithNoUsableUndoQueuesNoDelete() {
        Game game = newGame("db-sync-corrupt-reload");

        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameSaveService.when(() -> GameSaveService.save(game, "test save")).thenReturn(true);
            GameManager.save(game, "test save");
            gameLoadService.when(() -> GameLoadService.tryLoad(game.getName())).thenReturn(corruptResult());
            gameUndoService
                    .when(() -> GameUndoService.loadUndoForMissingGame(game.getName()))
                    .thenReturn(null);

            assertThat(GameManager.reload(game.getName())).isNull();

            // The file is still on disk, so the game must stay in the bot and keep its database rows.
            assertThat(GameManager.isValid(game.getName())).isTrue();
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(game), any()));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void gameWithNoFileQueuesDelete() {
        String missingGameName = uniqueName("db-sync-missing-file");

        try (MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            assertThat(GameManager.get(missingGameName)).isNull();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueDelete(missingGameName));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void gameWithCorruptFileQueuesNoDeleteAndStaysValid() {
        try (var harness = TestGameHarness.forDefaultMap();
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            String gameName = harness.getGameName();
            // Reloading registers the healthy game with the bot before its file gets corrupted.
            Game game = GameManager.reload(gameName);
            harness.corruptGameFile();

            assertThat(GameManager.get(gameName)).isNull();

            assertThat(GameManager.isValid(gameName)).isTrue();
            assertThat(GameManager.getManagedGame(gameName)).isNotNull();
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(game), any()));
            pipeline.verifyNoMoreInteractions();
        }
    }

    private static GameLoadResult.Corrupt corruptResult() {
        return new GameLoadResult.Corrupt(0L, new IllegalStateException("unparseable game file"));
    }

    @Test
    void warmupBuildsManagedGameFromDatabaseRowThatMatchesTheGameFile() {
        Map<String, GameLoadResult.Corrupt> corruptGames = new HashMap<>();
        String gameName = uniqueName("warmup-current");
        String userId = uniqueName("warmup-current-user");
        PersistedManagedGame persistedGame =
                new PersistedManagedGame(managedGameState(gameName, userId), new GameFileStamp(1000, 10));

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.getGameFileStamp(gameName))
                    .thenReturn(new GameFileStamp(1000, 10));

            assertThat(GameManager.warmupManagedGame(gameName, Map.of(gameName, persistedGame), corruptGames))
                    .isTrue();
            assertThat(corruptGames).isEmpty();

            gameLoadService.verify(() -> GameLoadService.tryLoad(gameName), never());
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
        Map<String, GameLoadResult.Corrupt> corruptGames = new HashMap<>();
        Game game = newGame("warmup-stale");
        String userId = game.getPlayers().keySet().iterator().next();
        PersistedManagedGame staleRow =
                new PersistedManagedGame(managedGameState(game.getName(), userId), new GameFileStamp(1000, 10));

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.getGameFileStamp(game.getName()))
                    .thenReturn(new GameFileStamp(2000, 10));
            gameLoadService
                    .when(() -> GameLoadService.tryLoad(game.getName()))
                    .thenReturn(new GameLoadResult.Loaded(game));

            assertThat(GameManager.warmupManagedGame(game.getName(), Map.of(game.getName(), staleRow), corruptGames))
                    .isFalse();
            assertThat(corruptGames).isEmpty();

            // The sync reloads the file when it runs, so warmup doesn't hold thousands of snapshots in the queue.
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile(game.getName()));
            pipeline.verifyNoMoreInteractions();
        }
        // The stale row said fog of war; the game file did not, and the file wins.
        assertThat(onlyGameOf(userId).isFowMode()).isFalse();
    }

    @Test
    void warmupLoadsGameFileAndQueuesSyncWhenTheGameHasNoDatabaseRow() {
        Map<String, GameLoadResult.Corrupt> corruptGames = new HashMap<>();
        Game game = newGame("warmup-missing-row");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.tryLoad(game.getName()))
                    .thenReturn(new GameLoadResult.Loaded(game));

            assertThat(GameManager.warmupManagedGame(game.getName(), Map.of(), corruptGames))
                    .isFalse();
            assertThat(corruptGames).isEmpty();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile(game.getName()));
            pipeline.verifyNoMoreInteractions();
        }
        assertThat(onlyGameOf(game.getPlayers().keySet().iterator().next()).getName())
                .isEqualTo(game.getName());
    }

    @Test
    void warmupWithoutTheDatabaseLoadsGameFileWithoutQueuingSync() {
        Map<String, GameLoadResult.Corrupt> corruptGames = new HashMap<>();
        Game game = newGame("warmup-no-database");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService
                    .when(() -> GameLoadService.tryLoad(game.getName()))
                    .thenReturn(new GameLoadResult.Loaded(game));

            assertThat(GameManager.warmupManagedGame(game.getName(), null, corruptGames))
                    .isFalse();
            assertThat(corruptGames).isEmpty();

            // With the database unavailable every sync would fail, so warmup must not flood the queue.
            pipeline.verifyNoInteractions();
        }
        assertThat(onlyGameOf(game.getPlayers().keySet().iterator().next()).getName())
                .isEqualTo(game.getName());
    }

    @Test
    void warmupRecordsCorruptGameFileWithoutBuildingOrSyncingIt() {
        Map<String, GameLoadResult.Corrupt> corruptGames = new HashMap<>();
        String gameName = uniqueName("warmup-corrupt");
        GameLoadResult.Corrupt corrupt = corruptResult();

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService.when(() -> GameLoadService.tryLoad(gameName)).thenReturn(corrupt);

            assertThat(GameManager.warmupManagedGame(gameName, Map.of(), corruptGames))
                    .isFalse();

            // A corrupt file must neither overwrite its database rows nor be treated as deleted.
            pipeline.verifyNoInteractions();
        }
        assertThat(corruptGames).containsExactly(Map.entry(gameName, corrupt));
    }

    private static AtomicBoolean recordWriteLockWhenSyncQueued(
            MockedStatic<GameDatabaseSyncPipeline> pipeline, Game game) {
        AtomicBoolean queuedUnderWriteLock = new AtomicBoolean();
        pipeline.when(() -> GameDatabaseSyncPipeline.queueSync(eq(game), any())).thenAnswer(_ -> {
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
                null,
                false,
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
                List.of(new ManagedGameState.Participant(userId, "Warmup User", true, true)),
                Map.of());
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
