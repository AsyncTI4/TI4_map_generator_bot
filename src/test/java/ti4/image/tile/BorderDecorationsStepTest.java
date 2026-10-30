package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_PADDING;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.testUtils.BaseTi4Test;

class BorderDecorationsStepTest extends BaseTi4Test {

    @Test
    void fullRatNestIsTheWholeImageOnceAndAPartialOneOnlyItsEdges() {
        BufferedImage allSides = renderRatNest(List.of(0, 1, 2, 3, 4, 5));
        BufferedImage oneSide = renderRatNest(List.of(0));

        assertThat(pixels(allSides)).isEqualTo(pixels(centredImage(allSides)));
        assertThat(painted(oneSide)).isPositive().isLessThan(painted(allSides));
        assertThat(painted(renderRatNest(List.of()))).isZero();
    }

    private static BufferedImage renderRatNest(List<Integer> directions) {
        Game game = new Game();
        game.newGameSetup();
        game.setName("border-decorations-test");
        Tile tile = new Tile("19", "101");
        game.setTile(tile);
        directions.forEach(direction -> game.addBorderAnomaly("101", direction, "ratnest"));
        return TileLayers.of(game, null, DisplayType.all).draw(tile, TileStep.Extras);
    }

    // What a single, unrotated, centred draw of the rat nest image looks like on the same layer.
    private static BufferedImage centredImage(BufferedImage layer) {
        BufferedImage expected = new BufferedImage(layer.getWidth(), layer.getHeight(), layer.getType());
        BufferedImage ratNest = ImageHelper.read(ImageResources.border("ratnest_border.png"));
        Graphics2D g = expected.createGraphics();
        g.drawImage(
                ratNest,
                TILE_PADDING + (TILE_WIDTH - ratNest.getWidth()) / 2,
                TILE_PADDING + (TILE_HEIGHT - ratNest.getHeight()) / 2,
                null);
        g.dispose();
        return expected;
    }

    private static long painted(BufferedImage image) {
        return pixels(image).stream().filter(pixel -> (pixel >>> 24) != 0).count();
    }

    private static List<Integer> pixels(BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                .boxed()
                .toList();
    }
}
