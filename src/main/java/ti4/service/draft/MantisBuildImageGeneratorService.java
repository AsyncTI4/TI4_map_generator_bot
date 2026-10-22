package ti4.service.draft;

import java.awt.image.BufferedImage;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.image.draft.MantisImage;
import ti4.image.io.ImageUploads;

@UtilityClass
public class MantisBuildImageGeneratorService {

    /**
     * Draw a simple image of the current map, and if optionally a tile which has been drawn
     * for a player to place.
     * @param mapBuildContext The context for building the map; contains the Game object.
     * @param uniqueKey A unique key to include in the file name.
     * @param currentPositions A list of tile positions to highlight on the map as available for the next tile.
     * @param pendingTileId A Tile ID to draw separately as pending placement by the current player.
     * @return A FileUpload containing the image, or null if the image could not be generated.
     */
    public FileUpload tryGenerateImage(
            MantisMapBuildContext mapBuildContext,
            String uniqueKey,
            List<String> currentPositions,
            String pendingTileId) {

        currentPositions = currentPositions == null ? List.of() : currentPositions;
        BufferedImage mapImage = MantisImage.render(mapBuildContext.game(), currentPositions, pendingTileId);
        if (mapImage == null) {
            return null;
        }

        return ImageUploads.createFileUpload(mapImage, uniqueKey);
    }
}
