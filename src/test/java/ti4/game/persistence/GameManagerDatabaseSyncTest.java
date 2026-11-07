package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class GameManagerDatabaseSyncTest extends BaseTi4Test {

    @Test
    void saveQueuesSyncAndDeleteQueuesDelete() {
        Game game = newGame("db-sync-save");

        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameSaveService.when(() -> GameSaveService.save(game, "test save")).thenReturn(true);
            gameSaveService.when(() -> GameSaveService.delete(game.getName())).thenReturn(true);

            GameManager.save(game, "test save");
            GameManager.delete(game.getName());

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(game), any()));
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueDelete(game.getName()));
            pipeline.verifyNoMoreInteractions();
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

            assertThat(GameManager.undo(sourceGame)).isSameAs(undoneGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(undoneGame), any()));
            pipeline.verifyNoMoreInteractions();
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

            assertThat(GameManager.reload(reloadedGame.getName())).isSameAs(reloadedGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(reloadedGame), any()));
            pipeline.verifyNoMoreInteractions();
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
    void reloadOfCorruptGameWithNoUsableUndoQueuesNoDelete() {
        Game game = newGame("db-sync-corrupt-reload");

        try (var harness = TestGameHarness.forDefaultMap();
                MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            // A real file on disk is what makes this game corrupt rather than missing.
            game.setName(harness.getGameName());
            gameSaveService.when(() -> GameSaveService.save(game, "test save")).thenReturn(true);
            GameManager.save(game, "test save");
            gameLoadService.when(() -> GameLoadService.load(game.getName())).thenReturn(null);
            gameUndoService
                    .when(() -> GameUndoService.loadUndoForMissingGame(game.getName()))
                    .thenReturn(null);

            assertThat(GameManager.reload(game.getName())).isNull();

            // The file is still on disk, so the game must stay in the bot and keep its database rows,
            // but its cached copy leaves memory so searches and crons stop seeing it, as get() does.
            assertThat(GameManager.isValid(game.getName())).isTrue();
            assertThat(GameManager.getManagedGames())
                    .extracting(ManagedGame::getName)
                    .doesNotContain(game.getName());
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
    void gameWithCorruptFileIsEvictedFromMemoryButKeepsItsNameAndDatabaseRows() {
        try (var harness = TestGameHarness.forDefaultMap();
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            String gameName = harness.getGameName();
            // Reloading registers the healthy game with the bot before its file gets corrupted.
            Game game = GameManager.reload(gameName);
            harness.corruptGameFile();

            assertThat(GameManager.get(gameName)).isNull();

            // The name stays so /developer reload_corrupted_saves can find it, but nothing iterates a null game.
            assertThat(GameManager.isValid(gameName)).isTrue();
            assertThat(GameManager.isCorrupt(gameName)).isTrue();
            assertThat(GameManager.getManagedGames())
                    .extracting(ManagedGame::getName)
                    .doesNotContain(gameName);
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(game), any()));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void warmupBuildsManagedGameFromDatabaseRowThatMatchesTheGameFile() {
        String gameName = uniqueName("warmup-current");
        String userId = uniqueName("warmup-current-user");
        ManagedGameState persistedGame = managedGameState(gameName, userId);

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameRevisionKeyFile> revisionKeys = mockStatic(GameRevisionKeyFile.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            revisionKeys.when(() -> GameRevisionKeyFile.read(gameName)).thenReturn("row-revision");

            assertThat(ManagedGameWarmup.warmupManagedGame(gameName, Map.of(gameName, persistedGame)))
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
        ManagedGameState staleRow = managedGameState(game.getName(), userId);

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameRevisionKeyFile> revisionKeys = mockStatic(GameRevisionKeyFile.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            revisionKeys.when(() -> GameRevisionKeyFile.read(game.getName())).thenReturn("newer-file-revision");
            gameLoadService.when(() -> GameLoadService.load(game.getName())).thenReturn(game);

            assertThat(ManagedGameWarmup.warmupManagedGame(game.getName(), Map.of(game.getName(), staleRow)))
                    .isFalse();

            // The sync reloads the file when it runs, so warmup doesn't hold thousands of snapshots in the queue.
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile(game.getName()));
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

            assertThat(ManagedGameWarmup.warmupManagedGame(game.getName(), Map.of()))
                    .isFalse();

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile(game.getName()));
            pipeline.verifyNoMoreInteractions();
        }
        assertThat(onlyGameOf(game.getPlayers().keySet().iterator().next()).getName())
                .isEqualTo(game.getName());
    }

    @Test
    void warmupWithoutTheDatabaseLoadsGameFileWithoutQueuingSync() {
        Game game = newGame("warmup-no-database");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService.when(() -> GameLoadService.load(game.getName())).thenReturn(game);

            assertThat(ManagedGameWarmup.warmupManagedGame(game.getName(), null))
                    .isFalse();

            // With the database unavailable every sync would fail, so warmup must not flood the queue.
            pipeline.verifyNoInteractions();
        }
        assertThat(onlyGameOf(game.getPlayers().keySet().iterator().next()).getName())
                .isEqualTo(game.getName());
    }

    @Test
    void warmupRecordsCorruptGameFileWithoutBuildingOrSyncingIt() {
        String gameName = uniqueName("warmup-corrupt");

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameLoadService.when(() -> GameLoadService.load(gameName)).thenReturn(null);

            assertThat(ManagedGameWarmup.warmupManagedGame(gameName, Map.of())).isFalse();

            // A corrupt file must neither overwrite its database rows nor be treated as deleted.
            pipeline.verifyNoInteractions();
        }
        assertThat(GameManager.getManagedGames())
                .extracting(ManagedGame::getName)
                .doesNotContain(gameName);
    }

    @Test
    void warmupFallsBackToTheGameFileWhenTheRevisionHeaderCannotBeRead() {
        String gameName = uniqueName("warmup-unreadable-header");
        ManagedGameState persistedGame = managedGameState(gameName, uniqueName("warmup-unreadable-user"));

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameRevisionKeyFile> revisionKeys = mockStatic(GameRevisionKeyFile.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            // Invalid UTF-8 in the header surfaces as an UncheckedIOException from the header reader.
            revisionKeys
                    .when(() -> GameRevisionKeyFile.read(gameName))
                    .thenThrow(new UncheckedIOException(new MalformedInputException(1)));
            gameLoadService.when(() -> GameLoadService.load(gameName)).thenReturn(null);

            assertThat(ManagedGameWarmup.warmupManagedGame(gameName, Map.of(gameName, persistedGame)))
                    .isFalse();
            pipeline.verifyNoInteractions();
        }
        // Warmup falls back to the file, which reports it like any other unreadable save, instead of throwing.
        assertThat(GameManager.getManagedGames())
                .extracting(ManagedGame::getName)
                .doesNotContain(gameName);
    }

    private static ManagedGame onlyGameOf(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        assertThat(managedPlayer).isNotNull();
        return managedPlayer.getGames().iterator().next();
    }

    private static ManagedGameState managedGameState(String gameName, String userId) {
        return new ManagedGameState(
                gameName,
                "row-revision",
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
                List.of(new ManagedGameState.Participant(userId, "Warmup User", true, false, false)),
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
