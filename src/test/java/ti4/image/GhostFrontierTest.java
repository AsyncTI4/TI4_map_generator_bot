package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class GhostFrontierTest extends BaseTi4Test {

    @Test
    void frontierIsTheHexRingAroundKnownSpaceMinusWhatIsKnown() {
        Set<String> known = Set.of("000", "101");

        Set<String> frontier = MapGenerator.frontier(known, known);

        Set<String> expected = new HashSet<>(MapFrame.positionsWithin("000", 1));
        expected.addAll(MapFrame.positionsWithin("101", 1));
        expected.removeAll(known);
        assertEquals(expected, frontier);
    }

    @Test
    void frontierDoesNotDependOnWhichHexesHoldRealTiles() {
        // No game is consulted at all: ghosts are pure hex-grid geometry, so they cannot reveal the map's shape.
        Set<String> frontier = MapGenerator.frontier(List.of("000"), Set.of("000"));

        assertEquals(Set.of("101", "102", "103", "104", "105", "106"), frontier);
    }

    @Test
    void onlySourcesSpreadButEveryKnownHexIsExcluded() {
        // 201 is known but not a source (e.g. outside the shown sector): it is neither spread from nor ghosted.
        Set<String> frontier = MapGenerator.frontier(List.of("101"), Set.of("101", "201"));

        assertTrue(frontier.contains("000"));
        assertFalse(frontier.contains("201"));
        assertFalse(frontier.contains("301"));
    }
}
