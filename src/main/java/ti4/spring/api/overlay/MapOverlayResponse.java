package ti4.spring.api.overlay;

import java.util.List;
import ti4.website.model.WebsiteOverlay;

public record MapOverlayResponse(List<WebsiteOverlay> overlays, long updatedAtEpochMs) {}
