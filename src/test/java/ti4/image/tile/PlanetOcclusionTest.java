package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Shape;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// The shapes follow the tile art: values in the resources corner, the name banner above or below the planet, running
// away from the values.
class PlanetOcclusionTest extends BaseTi4Test {

    @Test
    void topLeftValuesPutTheBannerAboveRunningRight() {
        double r = Mapper.getPlanet("maaluuk").getRadius();
        Shape shape = PlanetOcclusion.build(List.of("maaluuk"));
        double bannerY = 86 - (r + 8);
        assertThat(shape.contains(134, 86)).isTrue();
        assertThat(shape.contains(134 + 60, bannerY)).isTrue();
        assertThat(shape.contains(134 + 60, 86 + r + 8)).isFalse();
    }

    @Test
    void bottomRightValuesPutTheBannerBelowRunningLeft() {
        double r = Mapper.getPlanet("starpoint").getRadius();
        Shape shape = PlanetOcclusion.build(List.of("starpoint"));
        assertThat(shape.contains(202 - 60, 207 + r + 8)).isTrue();
        assertThat(shape.contains(202 - 60, 207 - (r + 8))).isFalse();
    }
}
