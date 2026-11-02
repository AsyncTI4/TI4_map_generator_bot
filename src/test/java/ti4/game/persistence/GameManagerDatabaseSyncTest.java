package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.service.persistence.GameDatabaseSyncPipeline;
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

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(game));
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
        Game sourceGame = newGame("db-sync-undo-source");
        Game undoneGame = newGame("db-sync-undo-result");

        try (MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameUndoService.when(() -> GameUndoService.undo(sourceGame)).thenReturn(undoneGame);

            assertThat(GameManager.undo(sourceGame)).isSameAs(undoneGame);

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(undoneGame));
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

            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(reloadedGame));
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
