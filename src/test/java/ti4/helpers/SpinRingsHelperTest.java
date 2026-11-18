package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SpinRingsHelperTest {

    // The default spin moves custom hyperlane lanes with their tiles (like the custom spin); data that stays put is
    // kept unless a moved tile's data lands on its position.
    @Test
    void defaultSpinMovesLaneDataWithItsTiles() {
        Map<String, String> data = Map.of("101", "lanesA", "102", "lanesB", "999", "untouched");
        Map<String, String> moved = SpinRingsHelper.movedWithTheirTiles(data, Map.of("101", "102", "102", "103"));
        assertThat(moved)
                .containsExactlyInAnyOrderEntriesOf(Map.of("102", "lanesA", "103", "lanesB", "999", "untouched"));
    }
}
