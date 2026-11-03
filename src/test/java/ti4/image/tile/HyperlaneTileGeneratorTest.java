package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.HyperlaneStyle;
import ti4.service.map.CustomHyperlaneService;
import ti4.testUtils.BaseTi4Test;

class HyperlaneTileGeneratorTest extends BaseTi4Test {

    private static final String TWO_WAY = "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
    private static final String ONE_WAY = TWO_WAY.replace('1', '2');

    // Two-way tiles keep the original drawing, so the chosen style must not change a single pixel.
    @Test
    void twoWayTilesIgnoreTheStyle() {
        Game game = gameWith(TWO_WAY);
        int[] dashed = pixels(HyperlaneTileGenerator.generateHyperlaneTile(tile(), game, HyperlaneStyle.DASHED));
        for (HyperlaneStyle style : HyperlaneStyle.values()) {
            assertThat(pixels(HyperlaneTileGenerator.generateHyperlaneTile(tile(), game, style)))
                    .as(style.name())
                    .isEqualTo(dashed);
        }
    }

    @Test
    void oneWayTilesDrawEachStyleDifferently() {
        Game game = gameWith(ONE_WAY);
        int[] twoWay =
                pixels(HyperlaneTileGenerator.generateHyperlaneTile(tile(), gameWith(TWO_WAY), HyperlaneStyle.DASHED));
        int[][] styles = Arrays.stream(HyperlaneStyle.values())
                .map(style -> pixels(HyperlaneTileGenerator.generateHyperlaneTile(tile(), game, style)))
                .toArray(int[][]::new);
        for (int i = 0; i < styles.length; i++) {
            assertThat(styles[i]).isNotEqualTo(twoWay);
            for (int j = i + 1; j < styles.length; j++) {
                assertThat(styles[i])
                        .as(HyperlaneStyle.values()[i] + " vs " + HyperlaneStyle.values()[j])
                        .isNotEqualTo(styles[j]);
            }
        }
    }

    // "Hide unseen hyperlanes": a remembered hyperlane draws no lanes but a ripple in each corner (centre dot near
    // white).
    @Test
    void hiddenLanesLeaveARippleInEachCorner() {
        String full = gameWith(ONE_WAY).getCustomHyperlaneData().get("000");
        String none = CustomHyperlaneService.onlyLanes(full, "000", Set.of());
        BufferedImage image =
                HyperlaneTileGenerator.generateHyperlaneTile(tile(), full, none, true, HyperlaneStyle.DASHED);
        int rgb = image.getRGB(101, 25);
        assertThat(Math.min((rgb >> 16) & 0xff, Math.min((rgb >> 8) & 0xff, rgb & 0xff)))
                .isGreaterThan(200);
    }

    // The fanned lanes reuse the template curves: straight lanes have no control point, curves rotate with their first
    // side.
    @Test
    void curveControlPointsFollowTheTemplates() {
        assertThat(HyperlaneTileGenerator.curveControlPoint(0, 3)).isNull();
        Point2D small = HyperlaneTileGenerator.curveControlPoint(0, 1);
        assertThat(small.getX()).isEqualTo(197.25, offset(0.01));
        assertThat(HyperlaneTileGenerator.curveControlPoint(1, 0)).isEqualTo(small);
        assertThat(HyperlaneTileGenerator.curveControlPoint(5, 0))
                .isEqualTo(HyperlaneTileGenerator.curveControlPoint(0, 5));
    }

    private static Game gameWith(String typedMatrix) {
        Game game = new Game();
        game.setTile(tile());
        CustomHyperlaneService.insertData(game, "000", typedMatrix);
        return game;
    }

    private static Tile tile() {
        return new Tile("hl", "000");
    }

    private static int[] pixels(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
    }
}
