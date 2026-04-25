package ti4.spring.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;

class GameImageControllerTest {

    @Test
    void getAttachmentUrlRefreshesWhenMapImageDataIsMissing() {
        GameImageService gameImageService = mock(GameImageService.class);
        GameAttachmentUrlRefreshService refreshService = mock(GameAttachmentUrlRefreshService.class);
        GameImageController controller = new GameImageController(gameImageService, refreshService);
        ManagedGame managedGame = mock(ManagedGame.class);

        when(managedGame.isFowMode()).thenReturn(false);
        when(managedGame.getName()).thenReturn("pbd11223");
        when(gameImageService.getLatestMapImageData("pbd11223")).thenReturn(Optional.empty());
        when(refreshService.refreshAttachmentUrl("pbd11223"))
                .thenReturn(Optional.of("https://cdn.discordapp.com/new.png"));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd11223")).thenReturn(managedGame);

            var response = controller.getAttachmentUrl("pbd11223");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo("https://cdn.discordapp.com/new.png");
        }
    }

    @Test
    void getAttachmentUrlRefreshesWhenDiscordIdsAreMissing() {
        GameImageService gameImageService = mock(GameImageService.class);
        GameAttachmentUrlRefreshService refreshService = mock(GameAttachmentUrlRefreshService.class);
        GameImageController controller = new GameImageController(gameImageService, refreshService);
        ManagedGame managedGame = mock(ManagedGame.class);
        MapImageData mapImageData = new MapImageData();
        mapImageData.setGameName("pbd11223");
        mapImageData.setLatestMapImageName("map.png");
        mapImageData.setLatestDiscordGuildId(1L);

        when(managedGame.isFowMode()).thenReturn(false);
        when(managedGame.getName()).thenReturn("pbd11223");
        when(gameImageService.getLatestMapImageData("pbd11223")).thenReturn(Optional.of(mapImageData));
        when(refreshService.refreshAttachmentUrl("pbd11223"))
                .thenReturn(Optional.of("https://cdn.discordapp.com/new.png"));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd11223")).thenReturn(managedGame);

            var response = controller.getAttachmentUrl("pbd11223");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo("https://cdn.discordapp.com/new.png");
        }
    }
}
