package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HexReachTest {

    private static Point hex(int columns, int halfRows) {
        return new Point(columns * FocusIslands.COLUMN_STEP, halfRows * FocusIslands.HALF_ROW_STEP);
    }

    @Test
    void distanceCountsHexSteps() {
        assertEquals(1, HexReach.distance(hex(0, 0), hex(0, 2)));
        assertEquals(1, HexReach.distance(hex(0, 0), hex(1, -1)));
        assertEquals(2, HexReach.distance(hex(0, 0), hex(0, 4)));
        assertEquals(2, HexReach.distance(hex(0, 0), hex(2, 0)));
        assertEquals(3, HexReach.distance(hex(0, 0), hex(1, 5)));
    }

    @Test
    void partsJoinHexesWithinReach() {
        Map<String, Point> centres = new LinkedHashMap<>();
        centres.put("a", hex(0, 0));
        centres.put("b", hex(0, 4));
        centres.put("c", hex(0, 10));

        assertEquals(3, HexReach.parts(centres, 1).size());
        assertEquals(2, HexReach.parts(centres, 2).size());
        assertEquals(1, HexReach.parts(centres, Integer.MAX_VALUE).size());
    }

    @Test
    void bridgesFillTheEmptyHexesBetweenJoinedHexes() {
        List<Point> bridged = HexReach.withBridges(List.of(hex(0, 0), hex(0, 6)), 3);

        assertEquals(List.of(hex(0, 0), hex(0, 6), hex(0, 2), hex(0, 4)), bridged);
        assertEquals(2, HexReach.withBridges(List.of(hex(0, 0), hex(0, 6)), 2).size(), "too far apart to bridge");
        // Whole segments keep the plain shape, so a large board does not bridge every pair of hexes.
        assertEquals(
                List.of(hex(0, 0), hex(0, 6)), HexReach.withBridges(List.of(hex(0, 0), hex(0, 6)), Integer.MAX_VALUE));
    }
}
