package ti4.image;

import java.awt.image.BufferedImage;
import java.util.List;
import ti4.website.model.WebsiteOverlay;

record MapRenderResult(
        BufferedImage canvas, EncodedImage encoded, List<WebsiteOverlay> overlays, boolean eligibleForWebsite) {}
