package ti4.image;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import ti4.ResourceHelper;
import ti4.game.Player;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.FactionModel;

public final class FactionIcons {

    private static final int FULL_SIZE = 95;
    private static final int URL_ICON_PADDING = 5;
    private static final int UNICODE_CANVAS = 100;
    private static final int UNICODE_PADDING = 5;
    private static final int UNDERLAY_ALPHA_THRESHOLD = 127;
    private static final int OPAQUE_BLACK = 0xFF000000;
    private static final BasicStroke UNICODE_OUTLINE = new BasicStroke(4.0f);

    private FactionIcons() {}

    @Nullable
    public static BufferedImage of(Player player) {
        return scaled(player, FULL_SIZE, FULL_SIZE);
    }

    @Nullable
    public static BufferedImage scaled(Player player, float scale) {
        int size = (int) (FULL_SIZE * scale);
        return scaled(player, size, size);
    }

    @Nullable
    public static BufferedImage scaled(@Nullable Player player, int width, int height) {
        if (player == null) return null;
        if (player.hasCustomFactionEmoji()) {
            Emoji emoji = Emoji.fromFormatted(player.getFactionEmoji());
            if (emoji instanceof CustomEmoji custom) {
                return RemoteImages.readScaled(
                        custom.getImageUrl(), width - URL_ICON_PADDING, height - URL_ICON_PADDING);
            }
            if (emoji instanceof UnicodeEmoji unicode) {
                return ImageHelper.scale(ImageHelper.square(renderUnicode(unicode)), width, height);
            }
        }
        return ofFaction(player.getFaction(), width, height);
    }

    @Nullable
    public static BufferedImage ofFaction(String factionId, int width, int height) {
        String path = path(factionId);
        if (path == null) return null;
        return ImageHelper.readScaled(path, width, height);
    }

    @Nullable
    public static String path(String factionId) {
        if ("null".equals(factionId) || isBlank(factionId)) {
            return null;
        }
        String path = "coexist".equalsIgnoreCase(factionId)
                ? ResourceHelper.getInstance().getGeneralFile(factionId + ".png")
                : ResourceHelper.getInstance().getFactionFile(factionId + ".png");
        if (path == null) path = homebrewBasePath(factionId);
        if (path == null) path = ResourceHelper.getInstance().getFactionFile(capitalize(factionId) + ".png");
        if (path == null && !"fogalliance".equalsIgnoreCase(factionId) && !"generic".equalsIgnoreCase(factionId)) {
            BotLogger.warning("Could not find image file for faction icon: " + factionId);
        }
        return path;
    }

    public static void draw(Graphics2D g2, Player player, int x, int y, int width, int height) {
        BufferedImage icon = scaled(player, width, height);
        if (icon == null) return;
        g2.drawImage(icon, x, y, null);
    }

    public static void drawFaded(
            Graphics2D g2, Player player, int x, int y, int width, int height, @Nullable Float opacity) {
        BufferedImage icon = scaled(player, width, height);
        if (icon == null) return;
        boolean faded = opacity != null && !opacity.equals(1.0f);
        if (faded) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        g2.drawImage(icon, x, y, null);
        if (faded) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    public static void drawUnderlay(Graphics2D g2, Player player, int x, int y, int width, int height) {
        BufferedImage icon = scaled(player, width, height);
        if (icon == null) return;
        if (icon.getWidth() > width || icon.getHeight() > height) {
            BotLogger.error(
                    new LogOrigin(player),
                    "Could not display player's faction icon image",
                    new IllegalStateException(
                            "icon " + icon.getWidth() + "x" + icon.getHeight() + " exceeds " + width + "x" + height));
            return;
        }
        g2.drawImage(silhouette(icon, width, height), x, y, null);
    }

    private static BufferedImage silhouette(BufferedImage icon, int width, int height) {
        int iconWidth = icon.getWidth();
        int iconHeight = icon.getHeight();
        int[] pixels = icon.getRGB(0, 0, iconWidth, iconHeight, null, 0, iconWidth);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (pixels[i] >>> 24) > UNDERLAY_ALPHA_THRESHOLD ? OPAQUE_BLACK : 0;
        }
        BufferedImage underlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        underlay.setRGB(0, 0, iconWidth, iconHeight, pixels, 0, iconWidth);
        return underlay;
    }

    @Nullable
    private static String homebrewBasePath(String factionId) {
        FactionModel faction = Mapper.getFaction(factionId);
        if (faction == null) return null;
        return faction.getHomebrewReplacesID()
                .map(base -> ResourceHelper.getInstance().getFactionFile(base + ".png"))
                .orElse(null);
    }

    private static BufferedImage renderUnicode(UnicodeEmoji emoji) {
        BufferedImage canvas = new BufferedImage(UNICODE_CANVAS, UNICODE_CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setFont(Storage.getEmojiFont());
        String text = emoji.getFormatted();
        OutlinedText.draw(g2, text, 20, 60, Color.white, null, null, UNICODE_OUTLINE, Color.black);
        Rectangle bounds = g2.getFont()
                .createGlyphVector(g2.getFontRenderContext(), text)
                .getGlyphPixelBounds(0, g2.getFontRenderContext(), 20, 60);
        return canvas.getSubimage(
                bounds.x - UNICODE_PADDING,
                bounds.y - UNICODE_PADDING,
                bounds.width + UNICODE_PADDING * 2,
                bounds.height + UNICODE_PADDING * 2);
    }
}
