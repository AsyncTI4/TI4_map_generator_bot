package ti4.game.persistence;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntitySyncService;
import ti4.testUtils.BaseTi4Test;

class GameManagerPersistenceSyncTest extends BaseTi4Test {

    @Test
    void saveAndDeleteSyncPersistedGameState() {
        Game game = newGame("persist-save-delete");
        GameEntitySyncService syncService = mock(GameEntitySyncService.class);

        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<SpringContext> springContext = mockStatic(SpringContext.class)) {
            springContext.when(() -> SpringContext.getBean(GameEntitySyncService.class)).thenReturn(syncService);
            gameSaveService.when(() -> GameSaveService.save(game, "test save")).thenReturn(true);
            gameSaveService.when(() -> GameSaveService.delete(game.getName())).thenReturn(true);

            GameManager.save(game, "test save");
            GameManager.delete(game.getName());
        }

        verify(syncService).sync(game);
        verify(syncService).delete(game.getName());
        verifyNoMoreInteractions(syncService);
    }

    @Test
    void undoAndReloadSyncPersistedGameState() {
        Game sourceGame = newGame("persist-source");
        Game undoneGame = newGame("persist-undone");
        Game reloadedGame = newGame("persist-reloaded");
        Game repairedGame = newGame("persist-repaired");
        String missingGameName = uniqueGameName("persist-missing");
        GameEntitySyncService syncService = mock(GameEntitySyncService.class);

        try (MockedStatic<GameUndoService> gameUndoService = mockStatic(GameUndoService.class);
                MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<SpringContext> springContext = mockStatic(SpringContext.class)) {
            springContext.when(() -> SpringContext.getBean(GameEntitySyncService.class)).thenReturn(syncService);
            gameUndoService.when(() -> GameUndoService.undo(sourceGame)).thenReturn(undoneGame);
            gameLoadService.when(() -> GameLoadService.load(reloadedGame.getName())).thenReturn(reloadedGame);
            gameLoadService.when(() -> GameLoadService.load(repairedGame.getName())).thenReturn(null);
            gameUndoService
                    .when(() -> GameUndoService.loadUndoForMissingGame(repairedGame.getName()))
                    .thenReturn(repairedGame);
            gameLoadService.when(() -> GameLoadService.load(missingGameName)).thenReturn(null);
            gameUndoService.when(() -> GameUndoService.loadUndoForMissingGame(missingGameName)).thenReturn(null);

            assertSame(undoneGame, GameManager.undo(sourceGame));
            assertSame(reloadedGame, GameManager.reload(reloadedGame.getName()));
            assertSame(repairedGame, GameManager.reload(repairedGame.getName()));
            assertNull(GameManager.reload(missingGameName));
        }

        verify(syncService).sync(undoneGame);
        verify(syncService).sync(reloadedGame);
        verify(syncService).sync(repairedGame);
        verify(syncService).delete(missingGameName);
        verifyNoMoreInteractions(syncService);
    }

    private static Game newGame(String prefix) {
        Game game = new Game();
        game.setName(uniqueGameName(prefix));
        game.setLastModifiedDate(0);
        game.addPlayer(uniqueGameName(prefix + "-user"), "Test User");
        return game;
    }

    private static String uniqueGameName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
