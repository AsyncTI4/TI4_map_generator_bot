package ti4.image.map;

import java.awt.image.BufferedImage;
import java.util.List;
import ti4.image.io.EncodedImage;
import ti4.image.playerarea.PlayerAreaWarnings;
import ti4.website.model.WebsiteOverlay;

record MapRenderResult(
        BufferedImage canvas,
        EncodedImage encoded,
        List<WebsiteOverlay> overlays,
        boolean eligibleForWebsite,
        PlayerAreaWarnings playerAreaWarnings) {}
