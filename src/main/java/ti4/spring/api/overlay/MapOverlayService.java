package ti4.spring.api.overlay;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.game.persistence.GameManager;
import ti4.json.JsonMapperManager;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;
import ti4.website.model.WebsiteOverlay;
import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
public class MapOverlayService {

    private final MapOverlayDataRepository mapOverlayDataRepository;

    public void saveOverlays(String gameName, List<WebsiteOverlay> overlays) {
        if (DatabasePersistenceGate.isDisabled()) return;
        if (!GameManager.isValid(gameName)) return;
        String overlaysJson = JsonMapperManager.basic().writeValueAsString(overlays);
        MapOverlayData data =
                mapOverlayDataRepository.findById(gameName).orElseGet(() -> new MapOverlayData(gameName, null, null));
        data.setOverlaysJson(overlaysJson);
        data.setUpdatedAt(LocalDateTime.now());
        mapOverlayDataRepository.save(data);
    }

    Optional<MapOverlayResponse> getOverlays(String gameName) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.empty();
        if (!GameManager.isValid(gameName)) return Optional.empty();
        return mapOverlayDataRepository.findById(gameName).map(MapOverlayService::toResponse);
    }

    private static MapOverlayResponse toResponse(MapOverlayData data) {
        List<WebsiteOverlay> overlays =
                JsonMapperManager.basic().readValue(data.getOverlaysJson(), new TypeReference<>() {});
        return new MapOverlayResponse(overlays, toEpochMs(data.getUpdatedAt()));
    }

    private static long toEpochMs(LocalDateTime updatedAt) {
        if (updatedAt == null) return 0;
        return updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static MapOverlayService getBean() {
        return SpringContext.getBean(MapOverlayService.class);
    }
}
