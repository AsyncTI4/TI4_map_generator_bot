package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class GhostFrontierTest extends BaseTi4Test {

    // Ghost rings must come from hex-grid geometry alone (no game is consulted), so they cannot reveal which hexes
    // hold tiles. Known hexes are never ghosted, and hexes that are known but not sources do not spread rings.
    @Test
    void frontierIsPureGridGeometryAroundTheSources() {
        assertEquals(
                Set.of("101", "102", "103", "104", "105", "106"), MapGenerator.frontier(List.of("000"), Set.of("000")));

        Set<String> frontier = MapGenerator.frontier(List.of("101"), Set.of("101", "201"));
        assertEquals(
                Set.of("000", "102", "106", "202", "212"), frontier, "201 is known: neither ghosted nor spread from");
    }
}
