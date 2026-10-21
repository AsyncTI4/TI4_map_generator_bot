package ti4.image.draw;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import ti4.logging.BotLogger;

public final class TextWrap {

    private static final Pattern LINE_BREAK = Pattern.compile("[\\n\n]");

    record Lines(String first, @Nullable String second) {}

    private TextWrap() {}

    public static List<String> wrapAll(String text, FontMetrics metrics, int maxWidth, boolean breakAtLastSpace) {
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

    static String trimToWidth(String text, FontMetrics metrics, int maxWidth) {
        for (int i = 0; i < text.length(); i++) {
            if (metrics.stringWidth(text.substring(0, i + 1)) > maxWidth) {
                return text.substring(0, i);
            }
        }
        return text;
    }

    public static List<String> layoutLines(String text, FontMetrics metrics, int maxWidth) {
        List<String> paragraphs = new ArrayList<>(Arrays.asList(LINE_BREAK.split(text)));
        List<String> lines = new ArrayList<>();
        try {
            for (String paragraph : paragraphs) {
                String line = paragraph.trim();
                while (width(line, metrics) > maxWidth) {
                    int split = lastSpaceWithin(line, metrics, maxWidth);
                    if (split == -1) {
                        split = lastCharacterWithin(line, metrics, maxWidth);
                    }
                    if (split <= 0) {
                        throw new IllegalArgumentException("no prefix of \"" + line + "\" fits");
                    }
                    lines.add(line.substring(0, split).trim());
                    line = line.substring(split).trim();
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            BotLogger.warning("Error laying out text with width `" + maxWidth + "`:\n```" + text + "\n```", e);
            return paragraphs;
        }
        return lines;
    }

    private static int lastSpaceWithin(String line, FontMetrics metrics, int maxWidth) {
        int split = -1;
        int nextSpace = line.indexOf(' ');
        while (nextSpace != -1 && width(line.substring(0, nextSpace), metrics) < maxWidth) {
            split = nextSpace;
            nextSpace = line.indexOf(' ', split + 1);
        }
        return split;
    }

    private static int lastCharacterWithin(String line, FontMetrics metrics, int maxWidth) {
        for (int i = 1; i < line.length(); i++) {
            if (width(line.substring(0, i), metrics) > maxWidth) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int width(String text, FontMetrics metrics) {
        return text.isBlank() ? 0 : metrics.stringWidth(text);
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
