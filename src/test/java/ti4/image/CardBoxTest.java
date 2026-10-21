package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class CardBoxTest extends BaseTi4Test {

    private static final int CANVAS_WIDTH = 3000;
    private static final int RIGHT_EDGE_OF_BODY = 95 + 1076;

    private static int rightmostInkInBody(String body, boolean breakAtLastSpace) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CardBox.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setFont(Storage.getFont35());
            CardBox.drawText(g2, 0, 0, Color.BLACK, 33, "", body, breakAtLastSpace);
            assertEquals(Storage.getFont26().getSize(), g2.getFont().getSize());
        } finally {
            g2.dispose();
        }
        int rightmost = -1;
        for (int x = 0; x < CANVAS_WIDTH; x++) {
            for (int y = 40; y < CardBox.HEIGHT; y++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    rightmost = x;
                }
            }
        }
        return rightmost;
    }

    @Test
    void bodyTextThatFitsTwoLinesKeepsTheStandardSize() {
        String body = "(1) " + "word ".repeat(200);

        assertTrue(rightmostInkInBody(body, true) <= RIGHT_EDGE_OF_BODY);
    }

    @Test
    void bodyTextAFewWordsTooLongShrinksInsteadOfRunningOutOfTheCard() {
        String body = "(1) " + "word ".repeat(260);

        assertTrue(rightmostInkInBody(body, true) <= RIGHT_EDGE_OF_BODY);
    }

    @Test
    void bodyTextFarTooLongForTwoLinesIsCutShortInsideTheCard() {
        String body = "(1) " + "word ".repeat(2000);

        assertTrue(rightmostInkInBody(body, true) <= RIGHT_EDGE_OF_BODY);
        assertTrue(rightmostInkInBody(body, false) <= RIGHT_EDGE_OF_BODY);
    }
}
