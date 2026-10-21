package ti4.helpers.async;

import java.util.List;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.image.draw.ImageGrid;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.io.ImageUploads;
import ti4.model.TileModel;

// Jazz's Interactive Map Builder
final class JimboImageHelper {

    private static final int COLUMNS = 5;

    private JimboImageHelper() {}

    static FileUpload tilesImage(List<TileModel> tiles) {
        if (tiles.isEmpty()) return null;
        List<ImageGrid.Item> items = tiles.stream()
                .map(tile -> new ImageGrid.Item(ImageHelper.read(ImageResources.tilePath(tile)), tile.getAlias()))
                .toList();
        return ImageUploads.createFileUpload(ImageGrid.squareTiles(items, COLUMNS, Fonts.ti(32)), "jimboStuff");
    }
}
