package ti4.image.map;

import java.awt.image.BufferedImage;
import java.util.List;
import ti4.image.io.EncodedImage;
import ti4.image.io.ImageEncoder;
import ti4.website.model.WebsiteOverlay;

record MapRenderResult(BufferedImage canvas, List<WebsiteOverlay> overlays, boolean eligibleForWebsite) {

    EncodedImage encodeForDiscord() {
        return ImageEncoder.forDiscord(canvas);
    }
}
