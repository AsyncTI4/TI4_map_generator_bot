package ti4.spring.api.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.persistence.GameManager;
import ti4.model.TechnologyModel;
import ti4.website.model.WebsiteOverlay;

class MapOverlayServiceTest {

    @Test
    void savedOverlaysReadBackAsTypedResponse() {
        // Overlays are stored as JSON text, so this guards that WebsiteOverlay stays deserializable.
        MapOverlayDataRepository repository = mock(MapOverlayDataRepository.class);
        AtomicReference<MapOverlayData> stored = new AtomicReference<>();
        when(repository.findById("pbd11223")).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return stored.get();
        });
        TechnologyModel tech = mock(TechnologyModel.class);
        when(tech.getAlias()).thenReturn("gd");
        List<WebsiteOverlay> overlays = List.of(
                new WebsiteOverlay("Agenda Deck", "12 cards", List.of(1, 2, 3, 4)),
                new WebsiteOverlay(tech, List.of(5, 6, 7, 8)));
        MapOverlayService service = new MapOverlayService(repository);

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd11223")).thenReturn(true);

            service.saveOverlays("pbd11223", overlays);
            MapOverlayResponse response = service.getOverlays("pbd11223").orElseThrow();

            assertThat(response.updatedAtEpochMs()).isPositive();
            assertThat(response.overlays()).hasSize(2);
            assertThat(response.overlays().getFirst().getTitle()).isEqualTo("Agenda Deck");
            assertThat(response.overlays().getFirst().getText()).isEqualTo("12 cards");
            assertThat(response.overlays().getFirst().getBoxXYWH()).containsExactly(1, 2, 3, 4);
            assertThat(response.overlays().get(1).getDataModelID()).isEqualTo("gd");
            assertThat(response.overlays().get(1).getBoxXYWH()).containsExactly(5, 6, 7, 8);
        }
    }
}
