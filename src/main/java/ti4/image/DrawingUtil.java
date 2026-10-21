package ti4.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.game.Player;
import ti4.model.ColorModel;

// TODO: delegates kept only for PlayerAreaGenerator, TileGenerator and UnitRenderGenerator; inline and delete once
// their rewrite branches have merged.
public final class DrawingUtil {

    public static final int DISCORD_AVATAR_SIZE = RemoteImages.DISCORD_AVATAR_SIZE;

    private DrawingUtil() {}

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

    public static void superDrawStringCenteredDefault(Graphics graphics, String txt, int x, int y) {
        OutlinedText.drawCenteredWhiteOnBlack((Graphics2D) graphics, txt, x, y);
    }

    public static void superDrawStringCentered(
            Graphics g, String txt, int x, int y, Color textColor, Stroke outlineSize, Color outlineColor) {
        OutlinedText.drawCentered((Graphics2D) g, txt, x, y, textColor, outlineSize, outlineColor);
    }

    public static void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        OutlinedText.drawCentered((Graphics2D) g, text, rect, font);
    }

    public static List<String> layoutText(Graphics2D g2, String inputText, int maxWidth) {
        return TextWrap.layoutLines(inputText, g2.getFontMetrics(), maxWidth);
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

    public static void drawRedX(Graphics2D g2, int x, int y, int size, boolean thick) {
        ShapeDrawing.redX(g2, x, y, size, thick);
    }

    public static void drawRectWithTwoColorGradient(
            Graphics2D g2, Color mainColor, Color accentColor, int x, int y, int width, int height) {
        ShapeDrawing.gradientOutline(g2, mainColor, accentColor, x, y, width, height);
    }

    public static void drawRectWithTwoColorGradient(Graphics2D g2, Color mainColor, Color accentColor, Rectangle rect) {
        ShapeDrawing.gradientOutline(g2, mainColor, accentColor, rect);
    }

    public static void drawCCOfPlayer(
            Graphics graphics, String ccID, int x, int y, int ccCount, Player player, boolean hideFactionIcon) {
        PlayerTokens.drawCommandCounters((Graphics2D) graphics, ccID, x, y, ccCount, player, hideFactionIcon, true);
    }

    public static void getAndDrawControlToken(
            Graphics graphics, Player player, int x, int y, boolean hideFactionIcon, float scale) {
        PlayerTokens.readAndDrawControlToken((Graphics2D) graphics, player, x, y, hideFactionIcon, scale);
    }

    public static void drawControlToken(
            Graphics graphics,
            BufferedImage bottomTokenImage,
            Player player,
            int x,
            int y,
            boolean hideFactionIcon,
            float scale) {
        PlayerTokens.drawControlToken((Graphics2D) graphics, bottomTokenImage, player, x, y, hideFactionIcon, scale);
    }

    public static Player getPlayerByControlMarker(Iterable<Player> players, String controlID) {
        return PlayerTokens.ownerOfToken(players, controlID);
    }

    public static String getBlackWhiteFileSuffix(String colorID) {
        return ColorUtil.blackWhiteFileSuffix(colorID);
    }

    public static BufferedImage hexBorder(
            String hexBorderStyle, ColorModel color, List<Integer> openSides, float widthScale) {
        return HexOverlays.border(hexBorderStyle, color, openSides, widthScale);
    }

    public static BufferedImage hexBorder(String hexBorderStyle, ColorModel color, List<Integer> openSides) {
        return HexOverlays.border(hexBorderStyle, color, openSides);
    }

    public static BufferedImage getPlayerFactionIconImage(Player player) {
        return FactionIcons.of(player);
    }

    public static BufferedImage getFactionIconImageScaled(String factionID, int width, int height) {
        return FactionIcons.ofFaction(factionID, width, height);
    }

    public static void drawPlayerFactionIconImage(
            Graphics graphics, Player player, int x, int y, int width, int height) {
        FactionIcons.draw((Graphics2D) graphics, player, x, y, width, height);
    }

    public static void drawPlayerFactionIconImageOpaque(
            Graphics graphics, Player player, int x, int y, int width, int height, Float opacity) {
        FactionIcons.drawFaded((Graphics2D) graphics, player, x, y, width, height, opacity);
    }

    public static Image getUserDiscordAvatar(String userID) {
        return RemoteImages.discordAvatar(userID);
    }
}
