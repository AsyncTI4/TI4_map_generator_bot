package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Font;
import java.awt.FontMetrics;
import org.junit.jupiter.api.Test;

class TextWrapTest {

    private static final int CHARACTER_WIDTH = 10;

    private static final FontMetrics TEN_PIXELS_PER_CHARACTER = new FontMetrics(new Font("Dialog", Font.PLAIN, 12)) {
        @Override
        public int stringWidth(String text) {
            return CHARACTER_WIDTH * text.length();
        }
    };

    @Test
    void textThatFitsStaysOnOneLine() {
        TextWrap.Lines lines = TextWrap.wrapToWidth("fits easily", TEN_PIXELS_PER_CHARACTER, 200, true);

        assertEquals("fits easily", lines.first());
        assertNull(lines.second());
    }

    @Test
    void breakingAtTheLastSpaceNeverSplitsAWord() {
        TextWrap.Lines lines = TextWrap.wrapToWidth("alpha beta gamma delta", TEN_PIXELS_PER_CHARACTER, 150, true);

        assertEquals("alpha beta", lines.first());
        assertEquals("gamma delta", lines.second());
    }

    @Test
    void breakingMidWordFillsTheFirstLine() {
        TextWrap.Lines lines = TextWrap.wrapToWidth("alpha beta gamma delta", TEN_PIXELS_PER_CHARACTER, 150, false);

        assertEquals("alpha beta gamm", lines.first());
        assertEquals("a delta", lines.second());
    }

    @Test
    void aFirstWordWiderThanTheLineIsStillSplitInsteadOfLeavingTheFirstLineEmpty() {
        TextWrap.Lines lines = TextWrap.wrapToWidth("extraordinarily wide", TEN_PIXELS_PER_CHARACTER, 100, true);

        assertEquals("extraordin", lines.first());
        assertEquals("arily wide", lines.second());
    }

    @Test
    void textThatFitsIsNotCut() {
        assertEquals("short", TextWrap.ellipsized("short", TEN_PIXELS_PER_CHARACTER, 100));
    }

    @Test
    void textThatDoesNotFitEndsInAnEllipsisThatFits() {
        String cut = TextWrap.ellipsized("alpha beta gamma delta", TEN_PIXELS_PER_CHARACTER, 100);

        assertEquals("alpha b...", cut);
    }

    @Test
    void anEllipsisDoesNotFollowTrailingSpaces() {
        String cut = TextWrap.ellipsized("alpha beta gamma", TEN_PIXELS_PER_CHARACTER, 140);

        assertEquals("alpha beta...", cut);
    }
}
