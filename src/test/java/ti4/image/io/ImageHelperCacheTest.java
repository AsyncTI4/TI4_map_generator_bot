package ti4.image.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class ImageHelperCacheTest extends BaseTi4Test {

    @Test
    void leavingCacheAsFoundEvictsOnlyImagesItNewlyRead() {
        // Two tiles no other test draws, so their cache state is predictable.
        String alreadyCached = ImageResources.tile("st27_Supernova.png");
        String readDuringWork = ImageResources.tile("st28_Supernova.png");
        ImageHelper.read(alreadyCached);

        boolean drew = ImageHelper.leavingCacheAsFound(List.of(alreadyCached, readDuringWork), () -> {
            ImageHelper.read(alreadyCached);
            return ImageHelper.read(readDuringWork) != null;
        });

        assertThat(drew).isTrue();
        assertThat(ImageCache.isStaticImageCached(alreadyCached)).isTrue();
        assertThat(ImageCache.isStaticImageCached(readDuringWork)).isFalse();
    }
}
