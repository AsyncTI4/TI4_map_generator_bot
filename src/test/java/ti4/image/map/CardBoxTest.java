package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.image.io.Fonts;
import ti4.testUtils.BaseTi4Test;

class CardBoxTest extends BaseTi4Test {

    private static final int CANVAS_WIDTH = 3000;
    private static final int RIGHT_EDGE_OF_BODY = 95 + 1076;
    private static final int BODY_FIRST_BASELINE = 70;
    private static final int LINE_STEP = 26;
    private static final int BOTTOM_PADDING = 14;

    private static List<String> linesOf(String body) {
        return CardBox.bodyLines(CardBox.bodyMetrics(), body, true);
    }

    private static BufferedImage draw(List<String> lines) {
        int height = CardBox.heightFor(lines);
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, height + 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setFont(Fonts.ti(35));
            CardBox.drawText(g2, 0, 0, Color.BLACK, 33, "", lines);
            assertEquals(Fonts.ti(26).getSize(), g2.getFont().getSize());
        } finally {
            g2.dispose();
        }
        return image;
    }

    private static int rightmostInk(BufferedImage image) {
        int rightmost = -1;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 40; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    rightmost = x;
                }
            }
        }
        return rightmost;
    }

    private static int lowestInk(BufferedImage image) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    return y;
                }
            }
        }
        return -1;
    }

    @Test
    void oneAndTwoLinesKeepTheStandardCardHeight() {
        assertEquals(CardBox.HEIGHT, CardBox.heightFor(List.of("one")));
        assertEquals(CardBox.HEIGHT, CardBox.heightFor(List.of("one", "two")));
    }

    @Test
    void everyLineBeyondTheSecondAddsOneLineStep() {
        assertEquals(CardBox.HEIGHT + LINE_STEP, CardBox.heightFor(List.of("a", "b", "c")));
        assertEquals(CardBox.HEIGHT + 4 * LINE_STEP, CardBox.heightFor(List.of("a", "b", "c", "d", "e", "f")));
    }

    @Test
    void theLastLineKeepsTheBottomPaddingOfTheStandardCard() {
        int sixLines = CardBox.heightFor(List.of("a", "b", "c", "d", "e", "f"));

        assertEquals(BODY_FIRST_BASELINE + 5 * LINE_STEP + BOTTOM_PADDING, sixLines);
        assertEquals(CardBox.HEIGHT, BODY_FIRST_BASELINE + LINE_STEP + BOTTOM_PADDING);
    }

    @Test
    void bodyTextThatFitsTwoLinesStaysOnTwoLines() {
        List<String> lines = linesOf("(1) " + "word ".repeat(20));

        assertEquals(2, lines.size());
        assertTrue(rightmostInk(draw(lines)) <= RIGHT_EDGE_OF_BODY);
    }

    @Test
    void longBodyTextWrapsOntoMoreLinesAtTheStandardSizeWithoutCutting() {
        String body = "(1) " + "word ".repeat(1000);

        List<String> lines = linesOf(body);

        assertTrue(lines.size() > 10);
        assertEquals(body.strip(), String.join(" ", lines).strip());
        assertTrue(rightmostInk(draw(lines)) <= RIGHT_EDGE_OF_BODY);
    }

    @Test
    void wrappedTextStaysInsideTheGrownCard() {
        List<String> lines = linesOf("(1) " + "word ".repeat(700));

        assertTrue(lowestInk(draw(lines)) <= CardBox.heightFor(lines));
    }

    @Test
    void unbrokenTextIsWrappedMidWordInsteadOfRunningOffTheCard() {
        List<String> lines = CardBox.bodyLines(CardBox.bodyMetrics(), "w".repeat(600), false);

        assertTrue(lines.size() > 2);
        assertTrue(rightmostInk(draw(lines)) <= RIGHT_EDGE_OF_BODY);
    }
}
