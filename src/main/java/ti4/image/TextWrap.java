package ti4.image;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

final class TextWrap {

    record Lines(String first, @Nullable String second) {}

    private TextWrap() {}

    static List<String> wrapAll(String text, FontMetrics metrics, int maxWidth, boolean breakAtLastSpace) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (true) {
            Lines split = wrapToWidth(remaining, metrics, maxWidth, breakAtLastSpace);
            lines.add(split.first());
            if (split.second() == null || split.second().isEmpty()) {
                return lines;
            }
            remaining = split.second();
        }
    }

    static Lines wrapToWidth(String text, FontMetrics metrics, int maxWidth, boolean breakAtLastSpace) {
        int length = text.length();
        int cut = longestPrefixWithin(text, metrics, maxWidth);
        if (cut == length) {
            return new Lines(text, null);
        }
        if (breakAtLastSpace) {
            int space = text.lastIndexOf(' ', cut);
            if (space > 0) {
                return new Lines(text.substring(0, space), text.substring(space + 1));
            }
        }
        int progress = Math.max(cut, 1);
        return new Lines(text.substring(0, progress), text.substring(progress));
    }

    private static int longestPrefixWithin(String text, FontMetrics metrics, int maxWidth) {
        int length = text.length();
        if (metrics.stringWidth(text) <= maxWidth) {
            return length;
        }
        int fits = 0;
        int tooWide = length;
        while (tooWide - fits > 1) {
            int middle = (fits + tooWide) >>> 1;
            if (metrics.stringWidth(text.substring(0, middle)) <= maxWidth) {
                fits = middle;
            } else {
                tooWide = middle;
            }
        }
        return fits;
    }
}
