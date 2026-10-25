package ti4.image.playerarea;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import ti4.image.io.Fonts;

final class TechInitials {

    private TechInitials() {}

    static void draw(Graphics2D g2, String initials, Color foreground, int x, int y) {
        if (initials.length() == 2) {
            drawTwo(g2, initials, foreground, x, y);
        } else if (initials.length() == 3) {
            drawThree(g2, initials, foreground, x, y);
        } else {
            drawLarge(g2, initials.substring(0, 1), foreground, x, y);
        }
    }

    static void drawLarge(Graphics2D g2, String letter, Color foreground, int x, int y) {
        g2.setFont(Fonts.ti(48));
        int offset = 20 - g2.getFontMetrics().stringWidth(letter) / 2;
        drawOutlined(g2, letter, foreground, x + offset, y + 148, 2);
    }

    private static void drawTwo(Graphics2D g2, String initials, Color foreground, int x, int y) {
        String left = initials.substring(0, 1);
        String right = initials.substring(1, 2);
        g2.setFont(Fonts.ti(32));
        FontMetrics metrics = g2.getFontMetrics();
        int offsetLeft = Math.max(0, 10 - metrics.stringWidth(left) / 2);
        int offsetRight = Math.min(40 - metrics.stringWidth(right), 30 - metrics.stringWidth(right) / 2);
        drawOutlined(g2, right, foreground, x + offsetRight, y + 148, 1);
        drawOutlined(g2, left, foreground, x + offsetLeft, y + 139, 1);
    }

    private static void drawThree(Graphics2D g2, String initials, Color foreground, int x, int y) {
        String left = initials.substring(0, 1);
        String middle = initials.substring(1, 2);
        String right = initials.substring(2, 3);
        g2.setFont(Fonts.ti(24));
        FontMetrics metrics = g2.getFontMetrics();
        int offsetLeft = Math.max(0, 7 - metrics.stringWidth(left) / 2);
        int offsetMiddle = 20 - metrics.stringWidth(middle) / 2;
        int offsetRight = Math.min(40 - metrics.stringWidth(right), 33 - metrics.stringWidth(right) / 2);
        drawOutlined(g2, right, foreground, x + offsetRight, y + 148, 1);
        drawOutlined(g2, middle, foreground, x + offsetMiddle, y + 141, 1);
        drawOutlined(g2, left, foreground, x + offsetLeft, y + 134, 1);
    }

    private static void drawOutlined(Graphics2D g2, String text, Color foreground, int x, int y, int radius) {
        g2.setColor(Color.BLACK);
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                g2.drawString(text, x + i, y + j);
            }
        }
        g2.setColor(foreground);
        g2.drawString(text, x, y);
    }
}
