package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HexPackerTest {

    private static Point cell(int col, int row) {
        return new Point(col * FocusIslands.COLUMN_STEP, row * FocusIslands.HALF_ROW_STEP);
    }

    private static HexPacker.Shape shape(Point... centres) {
        Point top = centres[0];
        return new HexPacker.Shape(List.of(centres), List.of(new Rectangle(top.x - 100, top.y - 250, 200, 80)));
    }

    // An L: a column of four hexes with two more running off its bottom to the right.
    private static final HexPacker.Shape L_SHAPE =
            shape(cell(0, 0), cell(0, 2), cell(0, 4), cell(0, 6), cell(1, 7), cell(2, 8));

    @Test
    void gridNeighboursExactlyTouch() {
        assertTrue(HexPacker.hexesClear(cell(0, 0), cell(1, 1), 0));
        assertTrue(HexPacker.hexesClear(cell(0, 0), cell(0, 2), 0));
        assertFalse(HexPacker.hexesClear(cell(0, 0), cell(1, 1), HexPacker.GUTTER));
    }

    @Test
    void packedIslandsKeepTheGutterAndAreDeterministic() {
        List<HexPacker.Shape> shapes = List.of(L_SHAPE, shape(cell(9, 9)), shape(cell(-9, 3)), L_SHAPE);
        List<HexPacker.Placement> placements = HexPacker.pack(shapes);

        assertEquals(placements, HexPacker.pack(shapes));
        List<List<Point>> placed = new ArrayList<>();
        for (int index = 0; index < shapes.size(); index++) {
            Point offset = placements.get(index).offset();
            placed.add(shapes.get(index).centres().stream()
                    .map(centre -> new Point(centre.x + offset.x, centre.y + offset.y))
                    .toList());
        }
        for (int a = 0; a < placed.size(); a++) {
            for (int b = a + 1; b < placed.size(); b++) {
                Rectangle labelA = placements.get(a).label();
                Rectangle labelB = placements.get(b).label();
                for (Point p : placed.get(a)) {
                    for (Point q : placed.get(b)) {
                        assertTrue(HexPacker.hexesClear(p, q, HexPacker.GUTTER - 1), p + " vs " + q);
                    }
                    assertTrue(HexPacker.rectanglesClear(labelB, HexPacker.hexBox(p), HexPacker.GUTTER - 1));
                }
                for (Point q : placed.get(b)) {
                    assertTrue(HexPacker.rectanglesClear(labelA, HexPacker.hexBox(q), HexPacker.GUTTER - 1));
                }
                assertTrue(HexPacker.rectanglesClear(labelA, labelB, HexPacker.GUTTER - 1));
            }
        }
    }

    @Test
    void aSmallIslandNestsIntoTheNotchOfAnL() {
        HexPacker.Shape single = shape(cell(3, 3));
        Point offset = HexPacker.pack(List.of(L_SHAPE, single)).get(1).offset();
        Point placed = new Point(cell(3, 3).x + offset.x, cell(3, 3).y + offset.y);

        assertTrue(L_SHAPE.hexBounds().contains(placed), "single sits inside the L's box at " + placed);
    }

    @Test
    void anIslandIsPlacedOnTheSideItReallyLiesOn() {
        HexPacker.Shape block = shape(cell(0, 0), cell(1, 1), cell(0, 2), cell(-1, 1), cell(1, 3), cell(-1, 3));
        Point east = placedCentre(block, cell(12, 0));
        Point west = placedCentre(block, cell(-12, 0));

        assertTrue(east.x > 0, "east island at " + east);
        assertTrue(west.x < 0, "west island at " + west);
    }

    @Test
    void nothingIsPackedIntoTheHoleOfARing() {
        HexPacker.Shape ring = shape(cell(0, -2), cell(1, -1), cell(1, 1), cell(0, 2), cell(-1, 1), cell(-1, -1));
        assertTrue(ring.footprint().contains(cell(0, 0)), "the hole counts as taken");

        Point placed = placedCentre(ring, cell(0, 0));
        for (Point hex : ring.footprint()) {
            assertTrue(
                    HexPacker.hexesClear(hex, placed, HexPacker.GUTTER - 1), "single landed in the ring at " + placed);
        }
    }

    private static Point placedCentre(HexPacker.Shape anchor, Point far) {
        Point offset = HexPacker.pack(List.of(anchor, shape(far))).get(1).offset();
        return new Point(far.x + offset.x, far.y + offset.y);
    }
}
