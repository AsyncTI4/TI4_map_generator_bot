package ti4.spring.api.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;

class MapOverlayControllerTest {

    private static final String OVERLAYS_JSON = "[{\"boxXYWH\":[10,4910,90,90],\"title\":\"Discordant Stars\"}]";

    @Test
    void returnsStoredOverlaysForNonFowGame() {
        MapOverlayService service = mock(MapOverlayService.class);
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.isFowMode()).thenReturn(false);
        when(service.getOverlaysJson("pbd11223")).thenReturn(Optional.of(OVERLAYS_JSON));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd11223")).thenReturn(managedGame);

            var response = new MapOverlayController(service).get("pbd11223");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(OVERLAYS_JSON);
        }
    }

    @Test
    void returnsNotFoundWhenNoOverlaysStored() {
        MapOverlayService service = mock(MapOverlayService.class);
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.isFowMode()).thenReturn(false);
        when(service.getOverlaysJson("pbd11223")).thenReturn(Optional.empty());

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd11223")).thenReturn(managedGame);

            var response = new MapOverlayController(service).get("pbd11223");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void hidesOverlaysForFowGames() {
        // Overlays expose card and unit positions, so FoW games must never serve them publicly.
        MapOverlayService service = mock(MapOverlayService.class);
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.isFowMode()).thenReturn(true);

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("fow123")).thenReturn(managedGame);

            var response = new MapOverlayController(service).get("fow123");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verifyNoInteractions(service);
        }
    }
}
