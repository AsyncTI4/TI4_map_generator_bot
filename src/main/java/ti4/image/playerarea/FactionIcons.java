package ti4.image.playerarea;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.image.DrawingUtil;
import ti4.logging.BotLogger;

final class FactionIcons {

    private static final int INNER_RADIUS = 1;
    private static final int OUTER_RADIUS = 2 + INNER_RADIUS;

    private FactionIcons() {}

    static void draw(Graphics2D g2, String faction, int x, int y, int width, int height) {
        draw(g2, faction, x, y, width, height, null, false);
    }

    static void drawBordered(Graphics2D g2, String faction, int x, int y, int width, int height, boolean border) {
        draw(g2, faction, x, y, width, height, null, border);
    }

    static void drawOpaque(Graphics2D g2, String faction, int x, int y, int width, int height, float opacity) {
        draw(g2, faction, x, y, width, height, opacity, false);
    }

    private static void draw(
            Graphics2D g2, String faction, int x, int y, int width, int height, Float opacity, boolean border) {
        BufferedImage icon = DrawingUtil.getFactionIconImageScaled(faction, width, height);
        if (icon == null) {
            return;
        }
        if (border) {
            drawBorder(g2, faction, icon, x, y, width, height);
        }
        try {
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(icon, x, y, null);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.error("Could not display faction icon image: " + faction, e);
        }
    }

    private static void drawBorder(
            Graphics2D g2, String faction, BufferedImage icon, int x, int y, int width, int height) {
        try {
            BufferedImage mask = alphaMask(icon, width, height);
            BufferedImage maskInner =
                    new BufferedImage(width + 2 * INNER_RADIUS, height + 2 * INNER_RADIUS, BufferedImage.TYPE_INT_ARGB);
            BufferedImage maskOuter =
                    new BufferedImage(width + 2 * OUTER_RADIUS, height + 2 * OUTER_RADIUS, BufferedImage.TYPE_INT_ARGB);
            for (int i = -OUTER_RADIUS; i < width + OUTER_RADIUS; i++) {
                for (int j = -OUTER_RADIUS; j < height + OUTER_RADIUS; j++) {
                    int pxInner = 0;
                    int pxOuter = 0;
                    for (int u = -OUTER_RADIUS; u <= OUTER_RADIUS; u++) {
                        for (int v = -OUTER_RADIUS; v <= OUTER_RADIUS; v++) {
                            if (i + u < 0) continue;
                            if (j + v < 0) continue;
                            if (i + u >= width) continue;
                            if (j + v >= height) continue;
                            if (u * u + v * v <= INNER_RADIUS * INNER_RADIUS) {
                                pxInner = Math.max(pxInner, mask.getRGB(i + u, j + v) >>> 24);
                            }
                            if (u * u + v * v <= OUTER_RADIUS * OUTER_RADIUS) {
                                pxOuter = Math.max(pxOuter, mask.getRGB(i + u, j + v) >>> 24);
                            }
                        }
                    }
                    maskOuter.setRGB(i + OUTER_RADIUS, j + OUTER_RADIUS, (pxOuter << 24) | 0x00FFFFFF);
                    if (i + INNER_RADIUS < 0) continue;
                    if (j + INNER_RADIUS < 0) continue;
                    if (i + INNER_RADIUS >= width + 2 * INNER_RADIUS) continue;
                    if (j + INNER_RADIUS >= height + 2 * INNER_RADIUS) continue;
                    maskInner.setRGB(i + INNER_RADIUS, j + INNER_RADIUS, (pxInner << 24));
                }
            }
            g2.drawImage(maskOuter, x - OUTER_RADIUS, y - OUTER_RADIUS, null);
            g2.drawImage(maskInner, x - INNER_RADIUS, y - INNER_RADIUS, null);
        } catch (Exception e) {
            BotLogger.error("Could not display faction icon image border: " + faction, e);
        }
    }

    private static BufferedImage alphaMask(BufferedImage icon, int width, int height) {
        int[] pixels = icon.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] &= 0xFF000000;
        }
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        mask.setRGB(0, 0, width, height, pixels, 0, width);
        return mask;
    }
}
