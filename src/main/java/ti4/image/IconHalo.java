package ti4.image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

record IconHalo(BufferedImage outer, BufferedImage inner, int padding) {

    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;

    static IconHalo twoTone(BufferedImage icon, int innerBorder, int outerBorder, int padding) {
        return new IconHalo(
                dilate(icon, outerBorder, padding, WHITE), dilate(icon, innerBorder, padding, BLACK), padding);
    }

    static BufferedImage blackRing(BufferedImage icon, int border, int padding) {
        return dilate(icon, border, padding, BLACK);
    }

    void drawBehind(Graphics2D g, int iconX, int iconY) {
        g.drawImage(outer, iconX - padding, iconY - padding, null);
        g.drawImage(inner, iconX - padding, iconY - padding, null);
    }

    private static BufferedImage dilate(BufferedImage icon, int radius, int padding, int rgb) {
        int w = icon.getWidth();
        int h = icon.getHeight();
        BufferedImage halo = new BufferedImage(w + 2 * padding, h + 2 * padding, icon.getType());
        for (int i = -padding; i < w + padding; i++) {
            for (int j = -padding; j < h + padding; j++) {
                int bestAlpha = 0;
                for (int p = -padding; p <= padding; p++) {
                    if (i + p < 0 || i + p >= w) continue;
                    for (int q = -padding; q <= padding; q++) {
                        if (j + q < 0 || j + q >= h) continue;
                        if (p * p + q * q <= radius * radius) {
                            bestAlpha = Math.max(bestAlpha, icon.getRGB(i + p, j + q) >>> 24);
                        }
                    }
                }
                halo.setRGB(i + padding, j + padding, (bestAlpha << 24) | rgb);
            }
        }
        return halo;
    }
}
