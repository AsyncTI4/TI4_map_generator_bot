package ti4.image.draw;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ti4.image.io.ImageHelper;

public record IconHalo(BufferedImage outer, BufferedImage inner, int padding) {

    public record HaloedIcon(BufferedImage icon, IconHalo halo) {}

    private record TwoToneKey(String path, float scale, int innerBorder, int outerBorder, int padding) {}

    private record RingKey(String path, float scale, int border, int padding) {}

    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;
    private static final Map<TwoToneKey, HaloedIcon> TWO_TONE_CACHE = new ConcurrentHashMap<>();
    private static final Map<RingKey, BufferedImage> RING_CACHE = new ConcurrentHashMap<>();

    public static HaloedIcon scaledTwoTone(
            String path, BufferedImage source, float scale, int innerBorder, int outerBorder, int padding) {
        TwoToneKey key = new TwoToneKey(path, scale, innerBorder, outerBorder, padding);
        return TWO_TONE_CACHE.computeIfAbsent(key, _ -> {
            BufferedImage icon = ImageHelper.scale(source, scale);
            return new HaloedIcon(icon, twoTone(icon, innerBorder, outerBorder, padding));
        });
    }

    public static BufferedImage blackRing(String path, float scale, BufferedImage icon, int border, int padding) {
        return RING_CACHE.computeIfAbsent(
                new RingKey(path, scale, border, padding), _ -> dilate(icon, border, padding, BLACK));
    }

    private static IconHalo twoTone(BufferedImage icon, int innerBorder, int outerBorder, int padding) {
        return new IconHalo(
                dilate(icon, outerBorder, padding, WHITE), dilate(icon, innerBorder, padding, BLACK), padding);
    }

    public void drawBehind(Graphics2D g, int iconX, int iconY) {
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
