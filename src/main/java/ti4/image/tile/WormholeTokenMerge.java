package ti4.image.tile;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;
import ti4.model.WormholeModel.Wormhole;

final class WormholeTokenMerge {

    static final int MAX_WORMHOLES_PER_TOKEN = 3;
    private static final int DEFAULT_SIZE = 80;
    private static final Font LETTER_FONT = new Font(Font.SERIF, Font.BOLD, 40);
    private static final String REFERENCE_GLYPHS = "θψ";
    private static final Map<String, Float> HUE_BY_TOKEN = new ConcurrentHashMap<>();

    record Part(Wormhole wormhole, String tokenFile, int count) {}

    record Merged(List<Wormhole> wormholes, BufferedImage image) {}

    record DrawOrder(List<String> tokens, Map<String, Merged> merged) {}

    private WormholeTokenMerge() {}

    static DrawOrder apply(List<String> tokens, int minTokens) {
        Map<String, Merged> merged = new LinkedHashMap<>();
        Map<String, List<String>> keysByFirstToken = new HashMap<>();
        Set<String> replaced = new HashSet<>();
        for (List<String> family : mergeableFamilies(tokens)) {
            if (family.size() < Math.max(2, minTokens)) continue;
            List<String> keys = new ArrayList<>();
            for (List<Part> group : group(family)) {
                String key = cacheKey(group);
                merged.put(key, new Merged(group.stream().map(Part::wormhole).toList(), image(key, group)));
                keys.add(key);
            }
            keysByFirstToken.put(family.getFirst(), keys);
            replaced.addAll(family);
        }
        if (merged.isEmpty()) return new DrawOrder(tokens, Map.of());

        List<String> order = new ArrayList<>();
        for (String token : tokens) {
            if (!replaced.contains(token)) {
                order.add(token);
            } else if (keysByFirstToken.containsKey(token)) {
                order.addAll(keysByFirstToken.remove(token));
            }
        }
        return new DrawOrder(order, merged);
    }

    private static Collection<List<String>> mergeableFamilies(List<String> tokens) {
        return tokens.stream()
                .filter(WormholeTokenMerge::isMergeable)
                .collect(Collectors.partitioningBy(
                        token -> Mapper.getTokenWormholes(token).getFirst().isRatRace()))
                .values();
    }

    private static boolean isMergeable(String token) {
        return Mapper.isMergeableWormholeToken(token)
                && Mapper.getTokenWormholes(token).size() == 1;
    }

    static List<List<Part>> group(List<String> mergeableTokens) {
        Map<Wormhole, Part> parts = new EnumMap<>(Wormhole.class);
        for (String token : mergeableTokens) {
            Wormhole wormhole = Mapper.getTokenWormholes(token).getFirst();
            parts.merge(
                    wormhole,
                    new Part(wormhole, token, 1),
                    (existing, added) -> new Part(existing.wormhole(), existing.tokenFile(), existing.count() + 1));
        }
        List<Part> byHue = parts.values().stream()
                .sorted(Comparator.comparingDouble(WormholeTokenMerge::hue).thenComparing(Part::wormhole))
                .toList();
        int groups = (byHue.size() + MAX_WORMHOLES_PER_TOKEN - 1) / MAX_WORMHOLES_PER_TOKEN;
        List<List<Part>> dealt = new ArrayList<>();
        for (int i = 0; i < groups; i++) dealt.add(new ArrayList<>());
        for (int i = 0; i < byHue.size(); i++) dealt.get(i % groups).add(byHue.get(i));
        return dealt.stream()
                .map(group -> group.stream()
                        .sorted(Comparator.comparing(Part::wormhole))
                        .toList())
                .toList();
    }

    private static float hue(Part part) {
        return HUE_BY_TOKEN.computeIfAbsent(part.tokenFile(), file -> {
            String path = ImageResources.tokenOrAttachment(file);
            BufferedImage image = path == null ? null : ImageHelper.read(path);
            if (image == null) return 0f;
            Color tint = dominantTint(image);
            return Color.RGBtoHSB(tint.getRed(), tint.getGreen(), tint.getBlue(), null)[0];
        });
    }

    private static String cacheKey(List<Part> group) {
        return "wormholeMerge:"
                + group.stream()
                        .map(part -> part.tokenFile() + "x" + part.count())
                        .collect(Collectors.joining(","));
    }

    private static BufferedImage image(String key, List<Part> group) {
        List<BufferedImage> partImages = partImages(group);
        BufferedImage cached = ImageHelper.cached(key, () -> compose(group, partImages));
        return cached != null ? cached : compose(group, partImages);
    }

    private static List<BufferedImage> partImages(List<Part> parts) {
        return parts.stream()
                .map(part -> ImageResources.tokenOrAttachment(part.tokenFile()))
                .map(path -> path == null ? null : ImageHelper.read(path))
                .toList();
    }

    private static BufferedImage compose(List<Part> parts, List<BufferedImage> images) {
        int size = images.stream()
                .filter(image -> image != null)
                .mapToInt(BufferedImage::getWidth)
                .reduce(DEFAULT_SIZE, Math::max);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int n = parts.size();
        double center = size / 2.0;
        double start = n == 2 ? 135 : 90;
        double step = 360.0 / n;
        List<Color> tints = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BufferedImage image = images.get(i);
            tints.add(image == null ? Color.GRAY : dominantTint(image));
            Graphics2D wedge = clippedToWedge(g, size, start + i * step, step, n);
            if (image != null) wedge.drawImage(image, 0, 0, size, size, null);
            dimCenter(wedge, center, size * 0.40, tints.get(i));
            wedge.dispose();
        }
        if (n > 1) drawDividers(g, center, size * 0.40, start, step, n);
        for (int i = 0; i < n; i++) {
            Part part = parts.get(i);
            double mid = Math.toRadians(start + (i + 0.5) * step);
            double radius = n == 1 ? 0 : size * (n == 2 ? 0.24 : 0.27);
            double glyphHeight = size * (n == 1 ? 0.46 : n == 2 ? 0.38 : 0.32);
            double letterX = center + Math.cos(mid) * radius;
            double letterY = center - Math.sin(mid) * radius;
            Rectangle2D letter = drawGlyph(g, part.wormhole().symbol(), letterX, letterY, glyphHeight, tints.get(i));
            if (part.count() > 1) {
                drawGlyph(
                        g,
                        "×" + part.count(),
                        letter.getMaxX() + glyphHeight * 0.18,
                        letter.getMaxY() - glyphHeight * 0.12,
                        glyphHeight * 0.45,
                        Color.WHITE);
            }
        }
        g.dispose();
        return out;
    }

    private static Graphics2D clippedToWedge(Graphics2D g, int size, double startAngle, double extent, int n) {
        Graphics2D wedge = (Graphics2D) g.create();
        if (n > 1) {
            double far = size * 2.0;
            double center = size / 2.0;
            wedge.clip(new Arc2D.Double(center - far, center - far, 2 * far, 2 * far, startAngle, extent, Arc2D.PIE));
        }
        return wedge;
    }

    private static void dimCenter(Graphics2D g, double center, double radius, Color tint) {
        float[] hsb = Color.RGBtoHSB(tint.getRed(), tint.getGreen(), tint.getBlue(), null);
        Color dark = new Color(Color.HSBtoRGB(hsb[0], Math.min(1f, hsb[1] * 0.9f), 0.22f));
        g.setPaint(new RadialGradientPaint(
                (float) center, (float) center, (float) radius, new float[] {0f, 0.7f, 1f}, new Color[] {
                    withAlpha(dark, 245), withAlpha(dark, 200), withAlpha(dark, 0)
                }));
        g.fill(new Ellipse2D.Double(center - radius, center - radius, 2 * radius, 2 * radius));
    }

    private static void drawDividers(Graphics2D g, double center, double radius, double start, double step, int n) {
        Graphics2D d = (Graphics2D) g.create();
        d.clip(new Ellipse2D.Double(center - radius, center - radius, 2 * radius, 2 * radius));
        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians(start + i * step);
            Line2D line = new Line2D.Double(
                    center, center, center + Math.cos(angle) * radius * 2, center - Math.sin(angle) * radius * 2);
            d.setStroke(new BasicStroke(3.2f));
            d.setColor(new Color(15, 15, 15, 220));
            d.draw(line);
            d.setStroke(new BasicStroke(1.1f));
            d.setColor(new Color(255, 255, 255, 190));
            d.draw(line);
        }
        d.dispose();
    }

    private static Rectangle2D drawGlyph(Graphics2D g, String text, double x, double y, double height, Color tint) {
        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D reference = new TextLayout(REFERENCE_GLYPHS, LETTER_FONT, frc)
                .getOutline(null)
                .getBounds2D();
        Shape glyph = new TextLayout(text, LETTER_FONT, frc).getOutline(null);
        Rectangle2D bounds = glyph.getBounds2D();
        double scale = height / reference.getHeight();
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.scale(scale, scale);
        transform.translate(-bounds.getCenterX(), -bounds.getCenterY());
        Shape placed = transform.createTransformedShape(glyph);
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(10, 10, 10, 230));
        g.draw(placed);
        float[] hsb = Color.RGBtoHSB(tint.getRed(), tint.getGreen(), tint.getBlue(), null);
        g.setColor(new Color(Color.HSBtoRGB(hsb[0], Math.min(hsb[1], 0.18f), 1f)));
        g.fill(placed);
        return placed.getBounds2D();
    }

    private static Color dominantTint(BufferedImage image) {
        double sumX = 0;
        double sumY = 0;
        double saturation = 0;
        int counted = 0;
        float[] hsb = new float[3];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) < 128) continue;
                Color.RGBtoHSB((pixel >> 16) & 255, (pixel >> 8) & 255, pixel & 255, hsb);
                double weight = hsb[1] * hsb[2];
                sumX += Math.cos(hsb[0] * 2 * Math.PI) * weight;
                sumY += Math.sin(hsb[0] * 2 * Math.PI) * weight;
                saturation += hsb[1];
                counted++;
            }
        }
        if (counted == 0) return Color.GRAY;
        float hue = (float) (((Math.atan2(sumY, sumX) / (2 * Math.PI)) + 1) % 1);
        return new Color(Color.HSBtoRGB(hue, (float) (saturation / counted), 1f));
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
