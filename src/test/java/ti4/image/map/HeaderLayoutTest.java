package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeaderLayoutTest {

    private static final int LEFT = 10;
    private static final int WRAPPED_LEFT = 20;
    private static final int NAME_BASELINE = 3000;
    private static final int ROUND_WIDTH = 330;
    private static final int STRATEGY_LABEL_WIDTH = 40;
    private static final int TRACKER_OFFSET = 1200;

    private static HeaderLayout.Inputs inputs(
            int stripWidth, int nameEnd, int labels, int players, int turnOrderWidth, int decksWidth, boolean tracker) {
        return new HeaderLayout.Inputs(
                LEFT,
                WRAPPED_LEFT,
                NAME_BASELINE,
                nameEnd,
                Collections.nCopies(labels, STRATEGY_LABEL_WIDTH),
                stripWidth - 100,
                ROUND_WIDTH,
                stripWidth - ROUND_WIDTH - 100 * players,
                turnOrderWidth,
                decksWidth,
                stripWidth - HeaderLayout.RIGHT_MARGIN,
                tracker,
                stripWidth - TRACKER_OFFSET,
                stripWidth - HeaderLayout.TRACKER_SIZE - HeaderLayout.RIGHT_MARGIN);
    }

    @Test
    void aStripWithNothingInTheWayKeepsTheClassicPositions() {
        int stripWidth = 2940;
        HeaderLayout.Inputs in = inputs(stripWidth, 500, 8, 6, 300, 600, true);

        HeaderLayout.Placement placement = HeaderLayout.place(in);

        int labelsEnd = LEFT + 8 * (STRATEGY_LABEL_WIDTH + HeaderLayout.LABEL_SPACING);
        int baseline = NAME_BASELINE + 80;
        assertEquals(1, placement.labelRows().size());
        assertEquals(new Point(labelsEnd + 100, baseline), placement.round());
        assertEquals(new Point(labelsEnd + 100, NAME_BASELINE - 75), placement.decks());
        assertEquals(new Point(labelsEnd + 100 + ROUND_WIDTH + 100, baseline), placement.turnOrder());
        assertEquals(new Rectangle(stripWidth - 1200, NAME_BASELINE + 80 - 265, 300, 300), placement.tracker());
        assertEquals(baseline + 30, placement.bottom());
        assertEquals(0, placement.displaced());
    }

    @Test
    void withoutATrackerNothingIsReserved() {
        HeaderLayout.Placement placement = HeaderLayout.place(inputs(2940, 500, 8, 6, 300, 600, false));

        assertNull(placement.tracker());
    }

    @Test
    void aTurnOrderThatWouldRunIntoTheTrackerWrapsBelowIt() {
        int stripWidth = 2160;
        HeaderLayout.Inputs in = inputs(stripWidth, 500, 8, 8, 1500, 600, true);

        HeaderLayout.Placement placement = HeaderLayout.place(in);

        assertEquals(WRAPPED_LEFT, placement.turnOrder().x);
        assertEquals(NAME_BASELINE + 80 + 100, placement.turnOrder().y);
        assertNoOverlaps(in, placement);
    }

    @Test
    void theTrackerSlidesRightBeforeAnythingWraps() {
        int stripWidth = 2160;
        HeaderLayout.Inputs in = inputs(stripWidth, 500, 8, 5, 500, 600, true);

        HeaderLayout.Placement placement = HeaderLayout.place(in);

        assertTrue(placement.tracker().x > stripWidth - 1200);
        assertEquals(0, placement.displaced());
        assertNoOverlaps(in, placement);
    }

    @Test
    void manyStrategyCardsWrapIntoBalancedRows() {
        HeaderLayout.Inputs in = inputs(1000, 300, 16, 6, 0, 0, false);

        HeaderLayout.Placement placement = HeaderLayout.place(in);

        assertEquals(2, placement.labelRows().size());
        assertEquals(
                8,
                placement.labelRows().get(0).end()
                        - placement.labelRows().get(0).first());
        assertEquals(
                8,
                placement.labelRows().get(1).end()
                        - placement.labelRows().get(1).first());
        assertEquals(WRAPPED_LEFT, placement.labelRows().get(1).x());
    }

    @Test
    void trackerNeverOverlapsAnyPartAtAnyCanvasWidthOrPlayerCount() {
        int[] stripWidths = {1000, 1500, 2160, 2940, 3500, 5280, 5880};
        int[] nameEnds = {250, 700, 1400};
        int[] labelCounts = {0, 5, 8, 9};
        int[] deckWidths = {0, 585, 885, 1100};
        for (int stripWidth : stripWidths) {
            for (int players = 3; players <= 30; players++) {
                boolean mega = players >= 9;
                if (mega != stripWidth >= 5000) {
                    continue;
                }
                for (int nameEnd : nameEnds) {
                    if (nameEnd > stripWidth - HeaderLayout.TRACKER_SIZE - 150) {
                        continue;
                    }
                    for (int labels : labelCounts) {
                        for (int decks : deckWidths) {
                            for (boolean tracker : new boolean[] {false, true}) {
                                int turnOrderWidth = Math.max(190, 100 * players + 100);
                                HeaderLayout.Inputs in =
                                        inputs(stripWidth, nameEnd, labels, players, turnOrderWidth, decks, tracker);
                                assertNoOverlaps(in, HeaderLayout.place(in));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void decksThatCannotFitBesideTheNameMoveBelowTheStrip() {
        int stripWidth = 1000;
        HeaderLayout.Inputs in = inputs(stripWidth, 600, 5, 4, 0, 885, true);

        HeaderLayout.Placement placement = HeaderLayout.place(in);

        assertEquals(LEFT, placement.decks().x);
        assertTrue(placement.decks().y > NAME_BASELINE);
        assertTrue(placement.bottom() >= placement.decks().y + 90);
        assertNoOverlaps(in, placement);
    }

    @Test
    void theTrackerStaysInsideTheCanvasTop() {
        HeaderLayout.Inputs in = new HeaderLayout.Inputs(
                LEFT, WRAPPED_LEFT, 140, 500, List.of(40, 40), 2840, ROUND_WIDTH, 2000, 0, 600, 2920, true, 1740, 2620);

        HeaderLayout.Placement placement = HeaderLayout.place(in);

        assertTrue(placement.tracker().y >= 0);
        assertTrue(placement.bottom() >= placement.tracker().y + 300 - 5);
    }

    private static void assertNoOverlaps(HeaderLayout.Inputs in, HeaderLayout.Placement placement) {
        List<String> names = new ArrayList<>();
        List<Rectangle> boxes = new ArrayList<>();
        names.add("name");
        boxes.add(new Rectangle(in.left(), in.nameBaseline() - 72, in.nameEnd(), 96));
        for (HeaderLayout.LabelRow row : placement.labelRows()) {
            int x = row.x();
            for (int i = row.first(); i < row.end(); i++) {
                int width = in.labelWidths().get(i);
                names.add("label" + i);
                boxes.add(new Rectangle(x, row.baseline() - 50, width, 86));
                x += width + HeaderLayout.LABEL_SPACING;
            }
        }
        names.add("round");
        boxes.add(new Rectangle(placement.round().x, placement.round().y - 50, in.roundWidth(), 62));
        if (in.decksWidth() > 0) {
            names.add("decks");
            boxes.add(new Rectangle(placement.decks().x, placement.decks().y, in.decksWidth(), 90));
        }
        if (in.turnOrderWidth() > 0) {
            names.add("turnOrder");
            boxes.add(new Rectangle(placement.turnOrder().x, placement.turnOrder().y - 50, in.turnOrderWidth(), 92));
        }
        if (placement.tracker() != null) {
            names.add("tracker");
            boxes.add(placement.tracker());
        }
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(
                        boxes.get(i).intersects(boxes.get(j)),
                        names.get(i) + " " + boxes.get(i) + " overlaps " + names.get(j) + " " + boxes.get(j) + " for "
                                + in);
            }
        }
        for (Rectangle box : boxes) {
            assertTrue(
                    box.y + box.height <= placement.bottom() + 15,
                    "box " + box + " ends below the header bottom " + placement.bottom());
        }
    }
}
