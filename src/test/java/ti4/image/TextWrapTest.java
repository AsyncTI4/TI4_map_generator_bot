package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;
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
    void textOfAnyLengthIsWrappedOntoAsManyLinesAsItNeeds() {
        List<String> lines =
                TextWrap.wrapAll("alpha beta gamma delta epsilon zeta eta", TEN_PIXELS_PER_CHARACTER, 100, true);

        assertEquals(List.of("alpha beta", "gamma", "delta", "epsilon", "zeta eta"), lines);
    }

    @Test
    void wrappingAllLinesOfTextThatFitsGivesOneLine() {
        assertEquals(List.of("fits"), TextWrap.wrapAll("fits", TEN_PIXELS_PER_CHARACTER, 100, true));
    }

    @Test
    void wrappingAllLinesMidWordFillsEveryLine() {
        List<String> lines = TextWrap.wrapAll("abcdefghijklmnopqrstuvwxy", TEN_PIXELS_PER_CHARACTER, 100, false);

        assertEquals(List.of("abcdefghij", "klmnopqrst", "uvwxy"), lines);
    }

    @Test
    void aCharacterWiderThanTheLineStillMakesProgress() {
        List<String> lines = TextWrap.wrapAll("abc", TEN_PIXELS_PER_CHARACTER, 5, false);

        assertEquals(List.of("a", "b", "c"), lines);
    }

    @Test
    void aWordWiderThanTheLineIsSplitOnItsOwnLines() {
        List<String> lines = TextWrap.wrapAll("hi extraordinarily wide", TEN_PIXELS_PER_CHARACTER, 100, true);

        assertEquals(List.of("hi", "extraordin", "arily wide"), lines);
    }

    @Test
    void aTrailingSpaceNeverAddsAnEmptyLine() {
        assertEquals(List.of("alpha"), TextWrap.wrapAll("alpha ", TEN_PIXELS_PER_CHARACTER, 50, true));
    }
}
