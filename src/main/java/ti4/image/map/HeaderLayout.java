package ti4.image.map;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import ti4.image.draw.RowWrap;

final class HeaderLayout {

    static final int LABEL_SPACING = 25;
    static final int TRACKER_SIZE = 300;
    static final int RIGHT_MARGIN = 20;

    private static final int FIRST_ROW_BELOW_NAME = 80;
    private static final int ROW_STEP = 100;
    private static final int ROUND_GAP = 100;
    private static final int TRACKER_ABOVE_FIRST_ROW = 265;
    private static final int TRACKER_GAP = 25;
    private static final int TRACKER_BLEED_BELOW_STRIP = 5;
    private static final int BOTTOM_MARGIN = 30;
    private static final int DECKS_ABOVE_NAME_BASELINE = 75;
    private static final int DECKS_HEIGHT = 90;
    private static final int DECKS_BELOW_STRIP_GAP = 15;
    private static final int GLYPH_ABOVE_BASELINE = 50;
    private static final int LABEL_BELOW_BASELINE = 36;
    private static final int ROUND_BELOW_BASELINE = 12;
    private static final int TURN_ORDER_ABOVE_BASELINE = 50;
    private static final int TURN_ORDER_BELOW_BASELINE = 42;
    private static final int MAX_LINE_PUSHES = 50;

    private HeaderLayout() {}

    record Inputs(
            int left,
            int wrappedLeft,
            int nameBaseline,
            int nameEnd,
            List<Integer> labelWidths,
            int labelRightLimit,
            int roundWidth,
            int roundWrapFromX,
            int turnOrderWidth,
            int decksWidth,
            int flowRight,
            boolean trackerShown,
            int trackerX,
            int trackerFarRightX) {}

    record LabelRow(int first, int end, int x, int baseline, int endX) {}

    record Placement(
            List<LabelRow> labelRows,
            Point round,
            Point decks,
            Point turnOrder,
            @Nullable Rectangle tracker,
            int bottom,
            int displaced) {}

    static Placement place(Inputs in) {
        if (!in.trackerShown()) {
            return flow(in, null);
        }
        if (!preferredTrackerFits(in)) {
            return flow(in, in.trackerFarRightX());
        }
        Placement preferred = flow(in, in.trackerX());
        if (in.trackerFarRightX() <= in.trackerX()) {
            return preferred;
        }
        Placement slidRight = flow(in, in.trackerFarRightX());
        return isBetter(slidRight, preferred) ? slidRight : preferred;
    }

    private static boolean preferredTrackerFits(Inputs in) {
        int right = in.trackerX() + TRACKER_SIZE;
        boolean leftOfTheFlow = right <= in.left();
        boolean rightOfTheName = in.trackerX() >= in.left() + in.nameEnd() + TRACKER_GAP;
        return in.trackerX() >= 0 && (leftOfTheFlow || rightOfTheName);
    }

    private static boolean isBetter(Placement candidate, Placement incumbent) {
        if (candidate.bottom() != incumbent.bottom()) {
            return candidate.bottom() < incumbent.bottom();
        }
        return candidate.displaced() < incumbent.displaced();
    }

    private static Placement flow(Inputs in, @Nullable Integer trackerX) {
        Rectangle tracker =
                trackerX == null ? null : new Rectangle(trackerX, trackerTop(in), TRACKER_SIZE, TRACKER_SIZE);
        int displaced = 0;

        List<LabelRow> labelRows = labelRows(in, labelLimit(in, tracker));
        int stripBaseline = in.nameBaseline() + FIRST_ROW_BELOW_NAME;
        int stripEnd = in.left();
        if (!labelRows.isEmpty()) {
            LabelRow last = labelRows.getLast();
            stripBaseline = last.baseline();
            stripEnd = last.endX();
        }

        int roundX = stripEnd + ROUND_GAP;
        int roundBaseline = stripBaseline;
        if (roundX > in.roundWrapFromX()) {
            roundX = in.wrappedLeft();
            roundBaseline += ROW_STEP;
        }
        int pushes = 0;
        while (pushes++ < MAX_LINE_PUSHES && touchesTracker(tracker, roundBox(in, roundX, roundBaseline))) {
            roundX = in.wrappedLeft();
            roundBaseline += ROW_STEP;
            displaced++;
        }

        int turnOrderX = roundX + in.roundWidth() + ROUND_GAP;
        int turnOrderBaseline = roundBaseline;
        if (in.turnOrderWidth() > 0 && blocked(in, tracker, turnOrderBox(in, turnOrderX, turnOrderBaseline))) {
            turnOrderX = in.wrappedLeft();
            turnOrderBaseline += ROW_STEP;
            displaced++;
            pushes = 0;
            while (pushes++ < MAX_LINE_PUSHES
                    && touchesTracker(tracker, turnOrderBox(in, turnOrderX, turnOrderBaseline))) {
                turnOrderBaseline += ROW_STEP;
            }
        }

        int bottom = Math.max(roundBaseline, turnOrderBaseline) + BOTTOM_MARGIN;
        if (tracker != null) {
            bottom = Math.max(bottom, tracker.y + tracker.height - TRACKER_BLEED_BELOW_STRIP);
        }

        int decksTop = in.nameBaseline() - DECKS_ABOVE_NAME_BASELINE;
        int decksX = Math.max(in.left() + in.nameEnd(), roundX);
        if (in.decksWidth() > 0 && blocked(in, tracker, decksBox(in, decksX, decksTop))) {
            decksX = in.left() + in.nameEnd();
            displaced++;
            if (blocked(in, tracker, decksBox(in, decksX, decksTop))) {
                decksX = in.left();
                decksTop = bottom + DECKS_BELOW_STRIP_GAP;
                bottom = decksTop + DECKS_HEIGHT;
            }
        }

        return new Placement(
                labelRows,
                new Point(roundX, roundBaseline),
                new Point(decksX, decksTop),
                new Point(turnOrderX, turnOrderBaseline),
                tracker,
                bottom,
                displaced);
    }

    private static int trackerTop(Inputs in) {
        return Math.max(0, in.nameBaseline() + FIRST_ROW_BELOW_NAME - TRACKER_ABOVE_FIRST_ROW);
    }

    private static int labelLimit(Inputs in, @Nullable Rectangle tracker) {
        if (tracker == null || tracker.x <= in.left()) {
            return in.labelRightLimit();
        }
        return Math.min(in.labelRightLimit(), tracker.x - TRACKER_GAP);
    }

    private static List<LabelRow> labelRows(Inputs in, int rightLimit) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < in.labelWidths().size(); i++) {
            indices.add(i);
        }
        int maxRowWidth = rightLimit - in.left() + LABEL_SPACING;
        List<List<Integer>> rows =
                RowWrap.balancedRows(indices, i -> in.labelWidths().get(i) + LABEL_SPACING, 0, maxRowWidth);
        List<LabelRow> placed = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            int x = row == 0 ? in.left() : in.wrappedLeft();
            int baseline = in.nameBaseline() + FIRST_ROW_BELOW_NAME + row * ROW_STEP;
            int end = x;
            for (int index : rows.get(row)) {
                end += in.labelWidths().get(index) + LABEL_SPACING;
            }
            placed.add(new LabelRow(rows.get(row).getFirst(), rows.get(row).getLast() + 1, x, baseline, end));
        }
        return placed;
    }

    private static Rectangle roundBox(Inputs in, int x, int baseline) {
        return new Rectangle(
                x, baseline - GLYPH_ABOVE_BASELINE, in.roundWidth(), GLYPH_ABOVE_BASELINE + ROUND_BELOW_BASELINE);
    }

    private static Rectangle turnOrderBox(Inputs in, int x, int baseline) {
        return new Rectangle(
                x,
                baseline - TURN_ORDER_ABOVE_BASELINE,
                in.turnOrderWidth(),
                TURN_ORDER_ABOVE_BASELINE + TURN_ORDER_BELOW_BASELINE);
    }

    private static Rectangle decksBox(Inputs in, int x, int top) {
        return new Rectangle(x, top, in.decksWidth(), DECKS_HEIGHT);
    }

    private static boolean blocked(Inputs in, @Nullable Rectangle tracker, Rectangle box) {
        return touchesTracker(tracker, box) || box.x + box.width > in.flowRight();
    }

    private static boolean touchesTracker(@Nullable Rectangle tracker, Rectangle box) {
        if (tracker == null) {
            return false;
        }
        Rectangle keepClear =
                new Rectangle(tracker.x - TRACKER_GAP, tracker.y, tracker.width + 2 * TRACKER_GAP, tracker.height);
        return keepClear.intersects(box);
    }
}
