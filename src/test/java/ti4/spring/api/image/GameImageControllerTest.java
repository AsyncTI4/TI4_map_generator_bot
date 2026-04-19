package ti4.spring.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;

class GameImageControllerTest {

    @Test
    void getAttachmentUrlReturnsStoredAttachmentUrlWithoutRefreshing() {
        GameImageService gameImageService = mock(GameImageService.class);
        GameAttachmentUrlRefreshService refreshService = mock(GameAttachmentUrlRefreshService.class);
        GameImageController controller = new GameImageController(gameImageService, refreshService);
        ManagedGame managedGame = mock(ManagedGame.class);
        Game game = new Game();
        game.setName("pbd11223");

        when(managedGame.isFowMode()).thenReturn(false);
        when(managedGame.getGame()).thenReturn(game);
        when(gameImageService.getLatestAttachmentUrl("pbd11223"))
                .thenReturn(Optional.of("https://cdn.discordapp.com/stored.png"));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd11223")).thenReturn(managedGame);

            var response = controller.getAttachmentUrl("pbd11223");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo("https://cdn.discordapp.com/stored.png");
        }

        verifyNoInteractions(refreshService);
    }

    @Test
    void getAttachmentUrlRefreshesWhenStoredAttachmentUrlIsMissing() {
        GameImageService gameImageService = mock(GameImageService.class);
        GameAttachmentUrlRefreshService refreshService = mock(GameAttachmentUrlRefreshService.class);
        GameImageController controller = new GameImageController(gameImageService, refreshService);
        ManagedGame managedGame = mock(ManagedGame.class);
        Game game = new Game();
        game.setName("pbd11223");

        when(managedGame.isFowMode()).thenReturn(false);
        when(managedGame.getGame()).thenReturn(game);
        when(gameImageService.getLatestAttachmentUrl("pbd11223")).thenReturn(Optional.empty());
        when(gameImageService.getLatestMapImageData("pbd11223")).thenReturn(Optional.empty());
        when(refreshService.refreshAttachmentUrl(game)).thenReturn(Optional.of("https://cdn.discordapp.com/new.png"));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd11223")).thenReturn(managedGame);

            var response = controller.getAttachmentUrl("pbd11223");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo("https://cdn.discordapp.com/new.png");
        }
    }
}
