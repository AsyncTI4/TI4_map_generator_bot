package ti4.image.map;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import ti4.helpers.RenderRandom;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;

final class SegmentTitle {

    private static final String EASTER_EGG_NAME = "A wild brainchild";
    private static final int EASTER_EGG_ODDS = 200;
    private static final Color EASTER_EGG_TEXT = new Color(255, 200, 0);
    private static final int TITLE_CENTRE_Y = 110;
    private static final int EASTER_EGG_SIDE_MARGIN = 40;

    private SegmentTitle() {}

    static boolean isEasterEggRoll(int roll) {
        return roll == 0;
    }

    static void draw(RenderContext ctx, Graphics2D g2) {
        String label = ctx.board().segmentLabel();
        if (label == null) {
            return;
        }
        int width = ctx.canvas().width();
        if (isSectorTitle(ctx, label)
                && isEasterEggRoll(RenderRandom.nextInt("segmentTitle.easterEgg", EASTER_EGG_ODDS))) {
            String text = "Map: " + EASTER_EGG_NAME + "!";
            Font big = Fonts.ti(80);
            Font font = g2.getFontMetrics(big).stringWidth(text) < width - EASTER_EGG_SIDE_MARGIN ? big : Fonts.ti(64);
            drawTitle(g2, width, text, font, EASTER_EGG_TEXT, Strokes.STROKE_4, Color.BLACK);
        } else {
            drawTitle(g2, width, "Map: " + label, Fonts.ti(64), Color.WHITE, Strokes.STROKE_4, Color.BLACK);
        }
        g2.setFont(Fonts.ti(32));
    }

    private static boolean isSectorTitle(RenderContext ctx, String label) {
        MapSegment shownSegment = ctx.board().shownSegment();
        return shownSegment != null && !shownSegment.isFracture() && label.equals(shownSegment.displayName(ctx.game()));
    }

    private static void drawTitle(
            Graphics2D g2, int width, String text, Font font, Color color, Stroke stroke, Color outline) {
        g2.setFont(font);
        OutlinedText.draw(
                g2,
                text,
                width / 2,
                TITLE_CENTRE_Y,
                color,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                stroke,
                outline);
    }
}
