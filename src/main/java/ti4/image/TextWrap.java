package ti4.image;

import java.awt.FontMetrics;
import javax.annotation.Nullable;

final class TextWrap {

    record Lines(String first, @Nullable String second) {}

    private TextWrap() {}

    static Lines wrapToWidth(String text, FontMetrics metrics, int maxWidth, boolean breakAtLastSpace) {
        int length = text.length();
        int trimmed = 0;
        int width = metrics.stringWidth(text);
        while (width > maxWidth) {
            trimmed++;
            width = metrics.stringWidth(text.substring(0, length - trimmed));
        }
        if (trimmed == 0) {
            return new Lines(text, null);
        }
        if (breakAtLastSpace) {
            while (trimmed < length && text.charAt(length - trimmed) != ' ') {
                trimmed++;
            }
        }
        return new Lines(text.substring(0, length - trimmed), text.substring(length - trimmed));
    }
}
