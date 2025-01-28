package ti4.image;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.helpers.Storage;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.model.ColorModel;

@UtilityClass
public class DrawingUtil {

    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final int DELTA_Y = 26;

    public static void superDrawString(Graphics g, String txt, int x, int y, Color textColor, MapGenerator.HorizontalAlign h, MapGenerator.VerticalAlign v, Stroke outlineSize, Color outlineColor) {
        superDrawString((Graphics2D) g, txt, x, y, textColor, h, v, outlineSize, outlineColor);
    }

    /**
     *
     * @param g graphics object
     * @param txt string to print
     * @param x x-position of the string (Left side, unless horizontalAlignment is set)
     * @param y y-position of the string (Bottom side, unless verticalAlignment is set)
     * @param textColor
     * @param horizontalAlignment location of the provided x relative to the (default = Left)
     * @param verticalAlignment location of the provided y relative to the text (default = Bottom)
     * @param outlineSize use global variable "strokeX" where X = outline size e.g. stroke1 for 1px outline
     * @param outlineColor
     */
    public static void superDrawString(Graphics2D g, String txt, int x, int y, Color textColor, MapGenerator.HorizontalAlign horizontalAlignment, MapGenerator.VerticalAlign verticalAlignment,
        Stroke outlineSize, Color outlineColor) {
        if (txt == null) return;

        int width = g.getFontMetrics().stringWidth(txt);
        if (horizontalAlignment != null) {
            switch (horizontalAlignment) {
                case Center -> x -= width / 2.0;
                case Right -> x -= width;
                case Left -> {
                }
            }
        }

        int height = g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent();
        if (verticalAlignment != null) {
            switch (verticalAlignment) {
                case Center -> y += height / 2;
                case Top -> y += height;
                case Bottom -> {
                }
            }
        }

        if (outlineSize == null) outlineSize = stroke2;
        if (outlineColor == null && textColor == null) {
            outlineColor = Color.BLACK;
            textColor = Color.WHITE;
        }
        if (outlineColor == null) {
            g.drawString(txt, x, y);
        } else {
            drawStringOutlined(g, txt, x, y, outlineSize, outlineColor, textColor);
        }
    }

    private static void drawStringOutlined(Graphics2D g2, String text, int x, int y, Stroke outlineStroke, Color outlineColor, Color fillColor) {
        if (text == null) return;
        Color origColor = g2.getColor();
        AffineTransform originalTileTransform = g2.getTransform();
        Stroke origStroke = g2.getStroke();
        RenderingHints origHints = g2.getRenderingHints();

        GlyphVector gv = g2.getFont().createGlyphVector(g2.getFontRenderContext(), text);
        Shape textShape = gv.getOutline();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.translate(x, y);
        g2.setColor(outlineColor);
        g2.setStroke(outlineStroke);
        g2.draw(textShape);

        g2.setColor(fillColor);
        g2.fill(textShape);

        g2.setColor(origColor);
        g2.setStroke(origStroke);
        g2.setTransform(originalTileTransform);
        g2.setRenderingHints(origHints);
    }

    public static void drawCCOfPlayer(Graphics graphics, String ccID, int x, int y, int ccCount, Player player, boolean hideFactionIcon) {
        drawCCOfPlayer(graphics, ccID, x, y, ccCount, player, hideFactionIcon, true);
    }

    public static void drawCCOfPlayer(Graphics graphics, String ccID, int x, int y, int ccCount, Player player, boolean hideFactionIcon, boolean rightAlign) {
        String ccPath = Mapper.getCCPath(ccID);
        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);
            BufferedImage blankCC = ImageHelper.read(Mapper.getCCPath("command_blank.png"));

            BufferedImage factionImage = null;
            int centreCustomTokenHorizontally = 0;
            if (!hideFactionIcon) {
                factionImage = getPlayerFactionIconImageScaled(player, 45, 45);
                if (factionImage == null) {
                    hideFactionIcon = true;
                } else {
                    centreCustomTokenHorizontally = ccImage != null ? ccImage.getWidth() / 2 - factionImage.getWidth() / 2 : 0;
                }
            }

            int delta = rightAlign ? -20 : 20;
            if (ccCount == 0) {
                ccCount = 1;
                ccImage = blankCC;
                hideFactionIcon = true;
            }
            for (int i = 0; i < ccCount; i++) {
                graphics.drawImage(ccImage, x + (delta * i), y, null);
                if (!hideFactionIcon)
                    graphics.drawImage(factionImage, x + (delta * i) + centreCustomTokenHorizontally, y + DELTA_Y, null);
            }
        } catch (Exception e) {
            String gameName = "";
            if (player == null) gameName = "Null Player";
            if (player != null && player.getGame() == null) gameName = "Null Game";
            if (player != null && player.getGame() != null) gameName = player.getGame().getName();
            BotLogger.log("Ignored error during map generation for `" + gameName + "`", e);
        }
    }

    public static BufferedImage getPlayerFactionIconImage(Player player) {
        return getPlayerFactionIconImageScaled(player, 95, 95);
    }

    public static BufferedImage getPlayerFactionIconImageScaled(Player player, float scale) {
        int scaledWidth = (int) (95 * scale);
        int scaledHeight = (int) (95 * scale);
        return getPlayerFactionIconImageScaled(player, scaledWidth, scaledHeight);
    }

    @Nullable
    public static BufferedImage getPlayerFactionIconImageScaled(Player player, int width, int height) {
        if (player == null)
            return null;
        Emoji factionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
        if (player.hasCustomFactionEmoji() && factionEmoji instanceof CustomEmoji factionCustomEmoji) {
            int urlImagePadding = 5;
            return ImageHelper.readURLScaled(factionCustomEmoji.getImageUrl(), width - urlImagePadding, height - urlImagePadding);
        } else if (player.hasCustomFactionEmoji() && factionEmoji instanceof UnicodeEmoji uni) {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setFont(Storage.getEmojiFont());
            BasicStroke stroke4 = new BasicStroke(4.0f);
            superDrawString(g2, uni.getFormatted(), 20, 60, Color.white, null, null, stroke4, Color.black);
            GlyphVector gv = g2.getFont().createGlyphVector(g2.getFontRenderContext(), uni.getFormatted());
            Rectangle rect = gv.getGlyphPixelBounds(0, g2.getFontRenderContext(), 20, 60);
            int pad = 5;
            BufferedImage img2 = img.getSubimage(rect.x - pad, rect.y - pad, rect.width + pad * 2, rect.height + pad * 2);
            return ImageHelper.scale(ImageHelper.square(img2), width, height);
        }

        return getFactionIconImageScaled(player.getFaction(), width, height);
    }

    @Nullable
    public static BufferedImage getFactionIconImageScaled(String factionID, int width, int height) {
        String factionPath = getFactionIconPath(factionID);
        if (factionPath == null)
            return null;

        return ImageHelper.readScaled(factionPath, width, height);
    }

    @Nullable
    public static String getFactionIconPath(String factionID) {
        if ("null".equals(factionID) || StringUtils.isBlank(factionID)) {
            return null;
        }
        String factionFile = ResourceHelper.getInstance().getFactionFile(factionID + ".png");
        if (factionFile == null) {
            // Handle homebrew factions based on real factions
            if (Mapper.getFaction(factionID) != null && Mapper.getFaction(factionID).getHomebrewReplacesID().isPresent()) {
                factionFile = ResourceHelper.getInstance()
                    .getFactionFile(Mapper.getFaction(factionID).getHomebrewReplacesID().get() + ".png");
            }
        }
        if (factionFile == null) {
            if (factionID.equalsIgnoreCase("fogalliance")) {
                return null;
            }
            BotLogger.log("Could not find image file for faction icon: " + factionID);
        }
        return factionFile;
    }

    public static Image getPlayerDiscordAvatar(Player player) {
        return getUserDiscordAvatar(player.getUser());
    }

    public static Image getUserDiscordAvatar(User user) {
        try {
            if (user == null) return null;
            return ImageHelper.readURLScaled(user.getEffectiveAvatar().getUrl(), 32, 32);
        } catch (Exception e) {
            BotLogger.log("Could not get Avatar", e);
        }
        return null;
    }

    // use {@link MapGenerator#DrawingUtil.superDrawString()}
    @Deprecated
    public static void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        // Get the FontMetrics
        FontMetrics metrics = g.getFontMetrics(font);
        // Determine the X coordinate for the text
        int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        // Determine the Y coordinate for the text (note we add the ascent, as in java
        // 2d 0 is top of the screen)
        int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        // Set the font
        g.setFont(font);
        // Draw the String
        g.drawString(text, x, y);
    }

    public static void drawControlToken(Graphics graphics, BufferedImage bottomTokenImage, Player player, int x, int y, boolean hideFactionIcon, float scale) {
        graphics.drawImage(bottomTokenImage, x, y, null);

        if (hideFactionIcon)
            return;
        scale *= 0.50f;
        BufferedImage factionImage = DrawingUtil.getPlayerFactionIconImageScaled(player, scale);
        if (factionImage == null)
            return;

        int centreCustomTokenHorizontally = bottomTokenImage.getWidth() / 2 - factionImage.getWidth() / 2;
        int centreCustomTokenVertically = bottomTokenImage.getHeight() / 2 - factionImage.getHeight() / 2;

        graphics.drawImage(factionImage, x + centreCustomTokenHorizontally, y + centreCustomTokenVertically, null);
    }

    public static Player getPlayerByControlMarker(Iterable<Player> players, String controlID) {
        Player player = null;
        for (Player player_ : players) {
            if (player_.getColor() != null && player_.getFaction() != null) {
                String playerControlMarker = Mapper.getControlID(player_.getColor());
                String playerCC = Mapper.getCCID(player_.getColor());
                String playerSweep = Mapper.getSweepID(player_.getColor());
                if (controlID.equals(playerControlMarker) || controlID.equals(playerCC)
                    || controlID.equals(playerSweep)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    public static String getBlackWhiteFileSuffix(String colorID) {
        Set<String> lightColors = Set.of("ylw", "org", "pnk", "tan", "crm", "sns", "tqs", "gld", "lme", "lvn", "rse",
            "spr", "tea", "lgy", "eth", "pch", "tpl", "cqr", "ptb");
        if (lightColors.contains(colorID)) {
            return "_blk.png";
        }
        return "_wht.png";
    }

    public BufferedImage hexBorder(String hexBorderStyle, ColorModel color, List<Integer> openSides) {
        return hexBorder(color, openSides, hexBorderStyle.equals("solid"));
    }

    private BufferedImage hexBorder(ColorModel color, List<Integer> openSides, boolean solidLines) {
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        boolean rainbow = color.getName().endsWith("rainbow");

        float inlineSize = 3.0f;
        float outlineSize = 6.0f;
        // on, off, on, off, ....
        float[] dash = { solidLines ? 85.0f : 30.0f, solidLines ? 1000.0f : 17.0f, 30.0f, 1000.0f };
        float[] sparse = { 11.0f, 1000.0f };
        Stroke line = new BasicStroke(inlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f);
        Stroke outline = new BasicStroke(outlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f);
        Stroke lineSparse = new BasicStroke(inlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, sparse, 0.0f);
        Stroke outlineSparse = new BasicStroke(outlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, sparse, 0.0f);

        Color primary = color.primaryColor();
        Color secondary = color.secondaryColor();
        if (secondary == null) secondary = primary;
        if (color.getName().equals("black"))
            primary = secondary = Color.darkGray;

        List<Point> corners = List.of(new Point(88, 2), new Point(256, 2), new Point(342, 149), new Point(256, 296), new Point(88, 296), new Point(2, 149));
        //corners.forEach(c -> c.translate(10, 10)); // offset by 10 pixels so that our border can slightly overlap the bounds of the hex

        // Draw outlines
        g2.setColor(Color.BLACK);
        for (int i = 0; i < 6; i++) {
            if (openSides.contains(i)) g2.setStroke(outlineSparse);
            if (!openSides.contains(i)) g2.setStroke(outline);
            Point c1 = corners.get(i);
            Point c2 = corners.get((i + 1) % 6);
            g2.drawLine(c1.x, c1.y, c2.x, c2.y);
            g2.drawLine(c2.x, c2.y, c1.x, c1.y);
        }

        // Draw Real Colors
        g2.setStroke(line);
        for (int i = 0; i < 6; i++) {
            if (openSides.contains(i)) g2.setStroke(lineSparse);
            if (!openSides.contains(i)) g2.setStroke(line);

            Point c1 = corners.get(i);
            Point c2 = corners.get((i + 1) % 6);

            GradientPaint gpOne = null, gpTwo = null;
            if (rainbow) { // special handling for rainbow
                Point mid = new Point((c1.x + c2.x) / 2, (c1.y + c2.y) / 2);
                if (i % 2 == 0) {
                    gpOne = new GradientPaint(c1, Color.red, mid, Color.yellow);
                    gpTwo = new GradientPaint(mid, Color.yellow, c2, Color.green);
                } else {
                    gpOne = new GradientPaint(c1, Color.green, mid, Color.blue);
                    gpTwo = new GradientPaint(mid, Color.blue, c2, Color.red);
                }
            } else {
                if (i % 2 == 0) {
                    gpOne = new GradientPaint(c1, primary, c2, secondary);
                } else {
                    gpOne = new GradientPaint(c1, secondary, c2, primary);
                }
            }

            // Draw lines both directions so the dash is symmetrical
            g2.setPaint(gpOne);
            g2.drawLine(c1.x, c1.y, c2.x, c2.y);
            g2.setPaint(gpTwo);
            g2.drawLine(c2.x, c2.y, c1.x, c1.y);
        }

        return img;
    }

    public static void drawPlayerFactionIconImage(Graphics graphics, Player player, int x, int y, int width, int height) {
        drawPlayerFactionIconImageOpaque(graphics, player, x, y, width, height, null);
    }

    public static void drawPlayerFactionIconImageOpaque(Graphics graphics, Player player, int x, int y, int width, int height, Float opacity) {
        if (player == null)
            return;
        try {
            BufferedImage resourceBufferedImage = DrawingUtil.getPlayerFactionIconImageScaled(player, width, height);
            Graphics2D g2 = (Graphics2D) graphics;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity)
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(resourceBufferedImage, x, y, null);
            if (setOpacity)
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.log("Could not display player's faction icon image", e);
        }
    }
}
