package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderRandomTest {

    @Test
    void neutralModeIsConstantAndCounted() throws Exception {
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4));
        try (AutoCloseable ignored = RenderRandom.neutral()) {
            assertFalse(RenderRandom.oneIn("a", 2));
            assertEquals(9, RenderRandom.nextInt("b", 10));
            assertEquals(3, RenderRandom.nextInt("c", 1, 4));
            assertEquals(0.5, RenderRandom.nextDouble("d"));
            RenderRandom.shuffle("e", list);
            RenderRandom.oneIn("a", 2);
        }
        assertEquals(List.of(1, 2, 3, 4), list);
        assertEquals(Map.of("a", 2, "b", 1, "c", 1, "d", 1, "e", 1), RenderRandom.callCounts());
    }

    @Test
    void eagerModeTakesTheRareBranch() throws Exception {
        try (AutoCloseable ignored = RenderRandom.eager()) {
            assertTrue(RenderRandom.oneIn("a", 1000));
            assertEquals(0, RenderRandom.nextInt("b", 1000));
            assertEquals(1, RenderRandom.nextInt("c", 1, 4));
            assertEquals(0.0, RenderRandom.nextDouble("d"));
        }
    }

    @Test
    void productionModeIsRandomAndNotCounted() throws Exception {
        try (AutoCloseable ignored = RenderRandom.neutral()) {
            RenderRandom.oneIn("a", 2);
        }
        for (int i = 0; i < 100; i++) {
            int value = RenderRandom.nextInt("b", 1, 4);
            assertTrue(value >= 1 && value < 4);
        }
        assertEquals(Map.of("a", 1), RenderRandom.callCounts());
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        boolean reordered = false;
        for (int i = 0; i < 20 && !reordered; i++) {
            RenderRandom.shuffle("c", list);
            reordered = !list.equals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        }
        assertTrue(reordered);
    }
}
