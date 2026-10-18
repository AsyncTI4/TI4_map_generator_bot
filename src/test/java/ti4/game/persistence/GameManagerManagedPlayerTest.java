package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.service.persistence.GameDatabaseSyncPipeline;
import ti4.testUtils.BaseTi4Test;

class GameManagerManagedPlayerTest extends BaseTi4Test {

    @Test
    void saveRemovesGameFromPlayerWhoLeft() {
        String remainingUserId = uniqueName("remaining-user");
        String departedUserId = uniqueName("departed-user");
        Game game = newGame("managed-player-save");
        game.addPlayer(remainingUserId, "Remaining User");
        game.addPlayer(departedUserId, "Departed User");

        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameDatabaseSyncPipeline> ignored = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameSaveService
                    .when(() -> GameSaveService.save(any(Game.class), anyString()))
                    .thenReturn(true);

            GameManager.save(game, "both players");
            assertThat(gameNamesOf(departedUserId)).containsExactly(game.getName());

            game.removePlayer(departedUserId);
            GameManager.save(game, "one player left");
        }

        assertThat(gameNamesOf(departedUserId)).doesNotContain(game.getName());
        assertThat(gameNamesOf(remainingUserId)).containsExactly(game.getName());
        assertThat(GameManager.getManagedGame(game.getName()).getPlayerIds()).containsExactly(remainingUserId);
    }

    @Test
    void reloadRemovesGameFromPlayerMissingFromTheReloadedGame() {
        String remainingUserId = uniqueName("remaining-user");
        String departedUserId = uniqueName("departed-user");
        Game savedGame = newGame("managed-player-reload");
        savedGame.addPlayer(remainingUserId, "Remaining User");
        savedGame.addPlayer(departedUserId, "Departed User");

        // A different lastModifiedDate makes the cached ManagedGame stale, so reload must replace it.
        Game reloadedGame = new Game();
        reloadedGame.setName(savedGame.getName());
        reloadedGame.setLastModifiedDate(1);
        reloadedGame.addPlayer(remainingUserId, "Remaining User");

        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> ignored = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameSaveService
                    .when(() -> GameSaveService.save(any(Game.class), anyString()))
                    .thenReturn(true);
            gameLoadService
                    .when(() -> GameLoadService.load(savedGame.getName()))
                    .thenReturn(reloadedGame);

            GameManager.save(savedGame, "both players");
            GameManager.reload(savedGame.getName());
        }

        assertThat(gameNamesOf(departedUserId)).doesNotContain(savedGame.getName());
        assertThat(gameNamesOf(remainingUserId)).containsExactly(savedGame.getName());
    }

    private static Game newGame(String prefix) {
        Game game = new Game();
        game.setName(uniqueName(prefix));
        game.setLastModifiedDate(0);
        return game;
    }

    private static List<String> gameNamesOf(String userId) {
        return GameManager.getManagedPlayer(userId).getGames().stream()
                .map(ManagedGame::getName)
                .toList();
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
