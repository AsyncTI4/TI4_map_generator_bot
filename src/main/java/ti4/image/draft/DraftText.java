package ti4.image.draft;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;

final class DraftText {

    private DraftText() {}

    static void centered(Graphics2D graphics, String text, Color fill, int x, int y, BasicStroke outline) {
        OutlinedText.draw(graphics, text, x, y, fill, HorizontalAlign.Center, null, outline, Color.black);
    }
}
