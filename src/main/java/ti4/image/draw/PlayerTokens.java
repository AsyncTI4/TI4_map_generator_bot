package ti4.image.draw;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import ti4.game.Player;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.Mapper;

public final class PlayerTokens {

    private static final int COMMAND_COUNTER_ICON_SIZE = 45;
    private static final int COMMAND_COUNTER_ICON_DROP = 26;
    private static final int COMMAND_COUNTER_STEP = 20;
    private static final float CONTROL_TOKEN_ICON_SCALE = 0.50f;

    private PlayerTokens() {}

    public static void drawCommandCounters(
            Graphics2D g2,
            String commandCounterId,
            int x,
            int y,
            int count,
            Player player,
            boolean hideFactionIcon,
            boolean rightAlign) {
        try {
            BufferedImage counter = ImageHelper.read(ImageResources.commandToken(commandCounterId));
            BufferedImage blank = ImageHelper.read(ImageResources.commandToken("command_blank.png"));
            BufferedImage icon = hideFactionIcon
                    ? null
                    : FactionIcons.scaled(player, COMMAND_COUNTER_ICON_SIZE, COMMAND_COUNTER_ICON_SIZE);
            int iconOffset = icon != null && counter != null ? counter.getWidth() / 2 - icon.getWidth() / 2 : 0;
            if (count == 0) {
                count = 1;
                counter = blank;
                icon = null;
            }
            int step = rightAlign ? -COMMAND_COUNTER_STEP : COMMAND_COUNTER_STEP;
            for (int i = 0; i < count; i++) {
                g2.drawImage(counter, x + step * i, y, null);
                if (icon != null) {
                    g2.drawImage(icon, x + step * i + iconOffset, y + COMMAND_COUNTER_ICON_DROP, null);
                }
            }
        } catch (Exception e) {
            logIgnored(player, e);
        }
    }

    public static void readAndDrawControlToken(
            Graphics2D g2, @Nullable Player player, int x, int y, boolean hideFactionIcon, float scale) {
        String color = player == null || hideFactionIcon ? "gray" : player.getColor();
        BufferedImage token = ImageHelper.readScaled(ImageResources.commandToken(Mapper.getControlID(color)), scale);
        drawControlToken(g2, token, player, x, y, hideFactionIcon, scale);
    }

    public static void drawControlToken(
            Graphics2D g2,
            BufferedImage token,
            @Nullable Player player,
            int x,
            int y,
            boolean hideFactionIcon,
            float scale) {
        g2.drawImage(token, x, y, null);
        if (hideFactionIcon) return;
        BufferedImage icon = FactionIcons.scaled(player, scale * CONTROL_TOKEN_ICON_SCALE);
        if (icon == null) return;
        int iconX = x + token.getWidth() / 2 - icon.getWidth() / 2;
        int iconY = y + token.getHeight() / 2 - icon.getHeight() / 2;
        g2.drawImage(icon, iconX, iconY, null);
    }

    @Nullable
    public static Player ownerOfToken(Iterable<Player> players, String tokenId) {
        for (Player player : players) {
            if (player.getColor() == null || player.getFaction() == null) continue;
            String color = player.getColor();
            if (tokenId.equals(Mapper.getControlID(color))
                    || tokenId.equals(Mapper.getCCID(color))
                    || tokenId.equals(Mapper.getSweepID(color))) {
                return player;
            }
        }
        return null;
    }

    private static void logIgnored(@Nullable Player player, Exception e) {
        if (player == null) {
            BotLogger.error("Ignored error during map generation for `Null Player`", e);
            return;
        }
        String gameName =
                player.getGame() == null ? "Null Game" : player.getGame().getName();
        BotLogger.error(new LogOrigin(player), "Ignored error during map generation for `" + gameName + "`", e);
    }
}
