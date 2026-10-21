package ti4.image;

import java.awt.FontMetrics;
import javax.annotation.Nullable;

final class TextWrap {

    private static final String ELLIPSIS = "...";

    record Lines(String first, @Nullable String second) {}

    private TextWrap() {}

    static Lines wrapToWidth(String text, FontMetrics metrics, int maxWidth, boolean breakAtLastSpace) {
        int length = text.length();
        int cut = length;
        while (cut > 0 && metrics.stringWidth(text.substring(0, cut)) > maxWidth) {
            cut--;
        }
        if (cut == length) {
            return new Lines(text, null);
        }
        if (breakAtLastSpace) {
            int space = text.lastIndexOf(' ', cut);
            if (space > 0) {
                return new Lines(text.substring(0, space), text.substring(space + 1));
            }
        }
        return new Lines(text.substring(0, cut), text.substring(cut));
    }

    static String ellipsized(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String kept = text.stripTrailing();
        while (!kept.isEmpty() && metrics.stringWidth(kept + ELLIPSIS) > maxWidth) {
            kept = kept.substring(0, kept.length() - 1).stripTrailing();
        }
        return kept + ELLIPSIS;
    }
}
