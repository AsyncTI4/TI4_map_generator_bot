package ti4.image;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import ti4.ResourceHelper;
import ti4.discord.JdaService;
import ti4.game.Player;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.ColorModel;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
public class DrawingUtil {

    private static BasicStroke stroke(int size) {
        return new BasicStroke(size);
    }

    private static final int DELTA_Y = 26;
    public static final int DISCORD_AVATAR_SIZE = 32;

    public static void superDrawString(
            Graphics g,
            String txt,
            int x,
            int y,
            Color textColor,
            HorizontalAlign h,
            VerticalAlign v,
            Stroke outlineSize,
            Color outlineColor) {
        OutlinedText.draw((Graphics2D) g, txt, x, y, textColor, h, v, outlineSize, outlineColor);
    }

    public static void superDrawString(
            Graphics2D g,
            String txt,
            int x,
            int y,
            Color textColor,
            HorizontalAlign horizontalAlignment,
            VerticalAlign verticalAlignment,
            Stroke outlineSize,
            Color outlineColor) {
        OutlinedText.draw(g, txt, x, y, textColor, horizontalAlignment, verticalAlignment, outlineSize, outlineColor);
    }

    public static void superDrawStringCenteredDefault(Graphics2D g2, String txt, int x, int y) {
        OutlinedText.drawCenteredWhiteOnBlack(g2, txt, x, y);
    }

    public static void superDrawStringCenteredDefault(Graphics graphics, String txt, int x, int y) {
        OutlinedText.drawCenteredWhiteOnBlack((Graphics2D) graphics, txt, x, y);
    }

    public static void superDrawStringCentered(
            Graphics g, String txt, int x, int y, Color textColor, Stroke outlineSize, Color outlineColor) {
        OutlinedText.drawCentered((Graphics2D) g, txt, x, y, textColor, outlineSize, outlineColor);
    }

    public static void superDrawStringCentered(
            Graphics2D g2, String txt, int x, int y, Color textColor, Stroke outlineSize, Color outlineColor) {
        OutlinedText.drawCentered(g2, txt, x, y, textColor, outlineSize, outlineColor);
    }

    public static void drawRedX(Graphics2D g2, int x, int y, int size, boolean thick) {
        int offset = size / 2;
        int strokeSize = thick ? 6 : 4;
        RenderingHints origHints = g2.getRenderingHints();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // draw outline
        g2.setStroke(stroke(strokeSize));
        g2.setColor(Color.black);
        g2.drawLine(x - offset, y - offset, x + offset, y + offset);
        g2.drawLine(x - offset, y + offset, x + offset, y - offset);

        // draw overline
        g2.setStroke(stroke(strokeSize - 2));
        g2.setColor(Color.red);
        g2.drawLine(x - offset, y - offset, x + offset, y + offset);
        g2.drawLine(x - offset, y + offset, x + offset, y - offset);

        g2.setRenderingHints(origHints);
    }

    public static void drawCCOfPlayer(
            Graphics graphics, String ccID, int x, int y, int ccCount, Player player, boolean hideFactionIcon) {
        drawCCOfPlayer(graphics, ccID, x, y, ccCount, player, hideFactionIcon, true);
    }

    public static void drawCCOfPlayer(
            Graphics graphics,
            String ccID,
            int x,
            int y,
            int ccCount,
            Player player,
            boolean hideFactionIcon,
            boolean rightAlign) {
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
                    centreCustomTokenHorizontally =
                            ccImage != null ? ccImage.getWidth() / 2 - factionImage.getWidth() / 2 : 0;
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
                    graphics.drawImage(
                            factionImage, x + (delta * i) + centreCustomTokenHorizontally, y + DELTA_Y, null);
            }
        } catch (Exception e) {
            String gameName = "";
            if (player == null) gameName = "Null Player";
            if (player != null && player.getGame() == null) gameName = "Null Game";
            if (player != null && player.getGame() != null)
                gameName = player.getGame().getName();
            if (player != null) {
                BotLogger.error(new LogOrigin(player), "Ignored error during map generation for `" + gameName + "`", e);
            } else {
                BotLogger.error("Ignored error during map generation for `" + gameName + "`", e);
            }
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
        if (player == null) return null;
        Emoji factionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
        if (player.hasCustomFactionEmoji() && factionEmoji instanceof CustomEmoji factionCustomEmoji) {
            int urlImagePadding = 5;
            return ImageHelper.readURLScaled(
                    factionCustomEmoji.getImageUrl(), width - urlImagePadding, height - urlImagePadding);
        } else if (player.hasCustomFactionEmoji() && factionEmoji instanceof UnicodeEmoji uni) {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setFont(Storage.getEmojiFont());
            BasicStroke stroke4 = new BasicStroke(4.0f);
            superDrawString(g2, uni.getFormatted(), 20, 60, Color.white, null, null, stroke4, Color.black);
            GlyphVector gv = g2.getFont().createGlyphVector(g2.getFontRenderContext(), uni.getFormatted());
            Rectangle rect = gv.getGlyphPixelBounds(0, g2.getFontRenderContext(), 20, 60);
            int pad = 5;
            BufferedImage img2 =
                    img.getSubimage(rect.x - pad, rect.y - pad, rect.width + pad * 2, rect.height + pad * 2);
            return ImageHelper.scale(ImageHelper.square(img2), width, height);
        }

        return getFactionIconImageScaled(player.getFaction(), width, height);
    }

    @Nullable
    public static BufferedImage getFactionIconImageScaled(String factionID, int width, int height) {
        String factionPath = getFactionIconPath(factionID);
        if (factionPath == null) return null;

        return ImageHelper.readScaled(factionPath, width, height);
    }

    @Nullable
    public static String getFactionIconPath(String factionID) {
        if ("null".equals(factionID) || isBlank(factionID)) {
            return null;
        }
        String factionFile = ResourceHelper.getInstance().getFactionFile(factionID + ".png");
        if ("coexist".equalsIgnoreCase(factionID)) {
            factionFile = ResourceHelper.getInstance().getGeneralFile(factionID + ".png");
        }
        if (factionFile == null) {
            // Handle homebrew factions based on real factions
            if (Mapper.getFaction(factionID) != null
                    && Mapper.getFaction(factionID).getHomebrewReplacesID().isPresent()) {
                factionFile = ResourceHelper.getInstance()
                        .getFactionFile(Mapper.getFaction(factionID)
                                        .getHomebrewReplacesID()
                                        .get() + ".png");
            }
        }
        if (factionFile == null) {
            factionFile = ResourceHelper.getInstance().getFactionFile(capitalize(factionID) + ".png");
        }
        if (factionFile == null) {
            if ("fogalliance".equalsIgnoreCase(factionID) || "generic".equalsIgnoreCase(factionID)) {
                return null;
            }
            BotLogger.warning("Could not find image file for faction icon: " + factionID);
        }
        return factionFile;
    }

    @Nullable
    public static Image getUserDiscordAvatar(String userID) {
        try {
            User user = JdaService.jda.getUserById(userID);
            if (user == null) return null;
            return ImageHelper.readURLScaled(
                    user.getEffectiveAvatar().getUrl(), DISCORD_AVATAR_SIZE, DISCORD_AVATAR_SIZE);
        } catch (Exception e) {
            // BotLogger.error("Could not get Avatar", e);
        }
        return null;
    }

    public static void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        OutlinedText.drawCentered((Graphics2D) g, text, rect, font);
    }

    public static void getAndDrawControlToken(
            Graphics graphics, Player player, int x, int y, boolean hideFactionIcon, float scale) {
        String color = (player == null || hideFactionIcon) ? "gray" : player.getColor();
        BufferedImage controlToken = ImageHelper.readScaled(Mapper.getCCPath(Mapper.getControlID(color)), scale);
        drawControlToken(graphics, controlToken, player, x, y, hideFactionIcon, scale);
    }

    public static void drawControlToken(
            Graphics graphics,
            BufferedImage bottomTokenImage,
            Player player,
            int x,
            int y,
            boolean hideFactionIcon,
            float scale) {
        graphics.drawImage(bottomTokenImage, x, y, null);

        if (hideFactionIcon) return;
        scale *= 0.50f;
        BufferedImage factionImage = getPlayerFactionIconImageScaled(player, scale);
        if (factionImage == null) return;

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
                if (controlID.equals(playerControlMarker)
                        || controlID.equals(playerCC)
                        || controlID.equals(playerSweep)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    public static String getBlackWhiteFileSuffix(String colorID) {
        if ("black".equalsIgnoreCase(Mapper.getColor(colorID).getTextColor())) {
            return "_blk.png";
        }
        return "_wht.png";
    }

    public BufferedImage hexBorder(String hexBorderStyle, ColorModel color, List<Integer> openSides, float widthScale) {
        return hexBorder(color, openSides, "solid".equals(hexBorderStyle), widthScale);
    }

    public BufferedImage hexBorder(String hexBorderStyle, ColorModel color, List<Integer> openSides) {
        return hexBorder(color, openSides, "solid".equals(hexBorderStyle), 1.0f);
    }

    public BufferedImage tintedBackground(Color color, float alpha) {
        BufferedImage bgImg = new BufferedImage(345, 299, BufferedImage.TYPE_INT_ARGB);
        Polygon p = new Polygon();
        for (int i = 0; i < 6; i++) {
            int theta = i * 60;
            int x = Math.clamp(Math.round(172.0 * Math.cos(Math.toRadians(theta)) + 172.5), 0, 345);
            int y = Math.clamp(Math.round(172.0 * Math.sin(Math.toRadians(theta)) + 149.5), 0, 299);
            p.addPoint(x, y);
        }
        Graphics2D g2 = bgImg.createGraphics();
        g2.setColor(color);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.fillPolygon(p);
        return bgImg;
    }

    private BufferedImage hexBorder(ColorModel color, List<Integer> openSides, boolean solidLines, float widthScale) {
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        boolean rainbow = color.getName().endsWith("rainbow");

        float inlineSize = 3.0f * widthScale;
        float outlineSize = inlineSize + 3.0f;
        // on, off, on, off, ....
        float[] dash = {solidLines ? 85.0f : 30.0f, solidLines ? 1000.0f : 17.0f, 30.0f, 1000.0f};
        float[] sparse = {11.0f, 1000.0f};
        Stroke line = new BasicStroke(inlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f);
        Stroke outline = new BasicStroke(outlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f);
        Stroke lineSparse =
                new BasicStroke(inlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, sparse, 0.0f);
        Stroke outlineSparse =
                new BasicStroke(outlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, sparse, 0.0f);

        Color primary = color.getPrimaryColor();
        Color secondary = color.getSecondaryColor();
        if (secondary == null) secondary = primary;
        if ("black".equals(color.getName())) primary = secondary = Color.darkGray;

        List<Point> corners = List.of(
                new Point(88, 2),
                new Point(256, 2),
                new Point(342, 149),
                new Point(256, 296),
                new Point(88, 296),
                new Point(2, 149));
        // corners.forEach(c -> c.translate(10, 10)); // offset by 10 pixels so that our border can slightly overlap the
        // bounds of the hex

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

            GradientPaint gpOne, gpTwo = null;
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

    public static void drawPlayerFactionIconImage(
            Graphics graphics, Player player, int x, int y, int width, int height) {
        drawPlayerFactionIconImageOpaque(graphics, player, x, y, width, height, null);
    }

    public static void drawPlayerFactionIconImageOpaque(
            Graphics graphics, Player player, int x, int y, int width, int height, Float opacity) {
        BufferedImage resourceBufferedImage = getPlayerFactionIconImageScaled(player, width, height);
        if (resourceBufferedImage == null) return;
        try {
            Graphics2D g2 = (Graphics2D) graphics;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(resourceBufferedImage, x, y, null);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "Could not display player's faction icon image", e);
        }
    }

    public static void drawPlayerFactionIconImageUnderlay(
            Graphics graphics, Player player, int x, int y, int width, int height) {
        BufferedImage faction = getPlayerFactionIconImageScaled(player, width, height);
        if (faction == null) return;
        try {
            BufferedImage underlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < faction.getWidth(); i++) {
                for (int j = 0; j < faction.getHeight(); j++) {
                    long argb = Integer.toUnsignedLong(faction.getRGB(i, j));
                    long alpha = (argb & 0xFF000000L) >> 24;
                    if (alpha > 127) {
                        underlay.setRGB(i, j, 0xFF000000);
                    }
                }
            }
            Graphics2D g2 = (Graphics2D) graphics;
            g2.drawImage(underlay, x, y, null);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "Could not display player's faction icon image", e);
        }
    }

    public static void drawEmoji(Graphics2D g2, TI4Emoji emoji, int x, int y, int size) {
        g2.drawImage(ImageHelper.readEmojiImageScaled(emoji, size), x, y, null);
    }

    public static List<String> layoutText(Graphics2D g2, String inputText, int maxWidth) {
        return TextWrap.layoutLines(inputText, g2.getFontMetrics(), maxWidth);
    }

    public static void drawRectWithTwoColorGradient(
            Graphics2D g2, Color mainColor, Color accentColor, int x, int y, int width, int height) {
        Rectangle rect = new Rectangle(x, y, width, height);
        drawRectWithTwoColorGradient(g2, mainColor, accentColor, rect);
    }

    public static void drawRectWithTwoColorGradient(Graphics2D g2, Color mainColor, Color accentColor, Rectangle rect) {
        Paint gradient = ColorUtil.gradient(mainColor, accentColor, rect);
        Paint old = g2.getPaint();
        g2.setPaint(gradient);
        g2.draw(rect);
        g2.setPaint(old);
    }

    public static void drawTextVertically(Graphics graphics, String text, int x, int y, Font font) {
        VerticalText.draw((Graphics2D) graphics, text, x, y, font);
    }

    public static void drawTextVertically(Graphics graphics, String text, int x, int y, Font font, boolean rightAlign) {
        VerticalText.draw((Graphics2D) graphics, text, x, y, font, rightAlign);
    }

    public static void drawTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, int maxWidth) {
        VerticalText.drawTwoLines((Graphics2D) graphics, text, x, y, maxWidth);
    }

    public static void drawOneOrTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, int maxWidth) {
        VerticalText.drawOneOrTwoLines((Graphics2D) graphics, text, x, y, maxWidth);
    }

    public static void drawOneOrTwoLinesOfTextVertically(
            Graphics graphics, String text, int x, int y, int maxWidth, boolean rightAlign) {
        VerticalText.drawOneOrTwoLines((Graphics2D) graphics, text, x, y, maxWidth, rightAlign);
    }

    public static void drawDebtBoxText(Graphics graphics, String text, int x, int y, int maxWidth) {
        VerticalText.drawWrappedColumns((Graphics2D) graphics, text, x, y, maxWidth);
    }
}
