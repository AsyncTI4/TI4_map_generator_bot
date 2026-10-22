package ti4.image.map;

import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.image.io.EncodedImage;
import ti4.image.io.ImageUploads;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.spring.api.overlay.MapOverlayService;
import ti4.website.AsyncTi4WebsiteHelper;

final class MapUploads {

    private MapUploads() {}

    static FileUpload toDiscord(Game game, MapRenderResult result, RenderTimings timings) {
        timings.startDiscord();
        EncodedImage encoded = result.encodeForDiscord();
        FileUpload fileUpload = ImageUploads.createFileUpload(encoded.bytes(), game.getName(), encoded.format());
        timings.stopDiscord();
        if (timings.isEnabled() && !AsyncTi4WebsiteHelper.uploadsEnabled()) {
            ImageUploads.saveLocalPng(result.canvas(), "MapDebug");
        }
        return fileUpload;
    }

    static void toWebsite(Game game, MapRenderResult result, RenderTimings timings) {
        timings.startWebsite();
        try {
            String testing = System.getenv("TESTING");
            if (testing == null && result.eligibleForWebsite()) {
                saveOverlays(game, result);
                AsyncTi4WebsiteHelper.putPlayerData(game.getID(), game);
            }
        } catch (Exception e) {
            BotLogger.error("Failed to send to game info to website", e);
        }
        timings.stopWebsite();
    }

    private static void saveOverlays(Game game, MapRenderResult result) {
        try {
            MapOverlayService.getBean().saveOverlays(game.getName(), result.overlays());
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to save website overlays", e);
        }
    }
}
