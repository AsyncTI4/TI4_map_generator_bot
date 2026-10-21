package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Set;
import ti4.game.Player;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;

final class FollowAndNeighboursLine {

    private static final int TEXT_INDENT = 9;
    private static final int BASELINE = 125;
    private static final int ICON_SIZE = 26;
    private static final int ICON_STEP = 27;
    private static final int LABEL_WIDTH = 115;

    private FollowAndNeighboursLine() {}

    static void draw(PlayerAreaContext ctx, Player player, boolean neighboursInHeader, int x, int y) {
        int xSpacer = drawUnfollowedStrategyCards(ctx, player, x, y);
        if (!ctx.game().isFowMode() && !neighboursInHeader) {
            drawNeighbours(ctx, player, x, y, xSpacer);
        }
    }

    private static void drawLabel(Graphics2D g2, String text, int x, int y, Color color) {
        OutlinedText.draw(g2, text, x, y, color, null, null, Strokes.STROKE_2, Color.black);
    }

    private static int drawUnfollowedStrategyCards(PlayerAreaContext ctx, Player player, int x, int y) {
        if (player.getUnfollowedSCs().isEmpty()) {
            return 0;
        }
        Graphics2D g2 = ctx.g2();
        g2.setFont(Fonts.ti(20));
        int drawX = x + TEXT_INDENT;
        int drawY = y + BASELINE;
        int xSpacer = 165;
        drawLabel(g2, "Needs to Follow: ", drawX, drawY, Color.red);
        for (int sc : player.getUnfollowedSCs()) {
            String drawText = String.valueOf(sc);
            int len = g2.getFontMetrics().stringWidth(drawText);
            drawLabel(g2, drawText, drawX + xSpacer, drawY, ColorUtil.getSCColor(sc, ctx.game(), true));
            xSpacer += len + 8;
        }
        return xSpacer;
    }

    private static void drawNeighbours(PlayerAreaContext ctx, Player player, int x, int y, int xSpacer) {
        Graphics2D g2 = ctx.g2();
        g2.setFont(Fonts.ti(20));
        int drawX = x + TEXT_INDENT;
        int drawY = y + BASELINE;
        Set<Player> neighbours = ctx.neighbours().withEquivalents(player);
        Set<Player> guildShips = GuildShipPartners.of(ctx.game(), player, neighbours);
        drawLabel(g2, neighbours.isEmpty() ? "No Neighbors" : "Neighbors: ", drawX + xSpacer, drawY, Color.red);
        xSpacer += LABEL_WIDTH;
        xSpacer = drawIcons(g2, neighbours, x + xSpacer, y) - x;
        if (guildShips.isEmpty()) {
            return;
        }
        g2.setFont(Fonts.ti(30));
        xSpacer += ICON_STEP;
        drawLabel(g2, "(", drawX + xSpacer, drawY + 2, Color.red);
        xSpacer += 20;
        xSpacer = drawIcons(g2, guildShips, x + xSpacer, y) - x;
        drawLabel(g2, ")", drawX + xSpacer - 8, drawY + 2, Color.red);
    }

    private static int drawIcons(Graphics2D g2, Set<Player> players, int x, int y) {
        for (Player other : players) {
            if (other.getFaction() != null) {
                FactionIcons.draw(g2, other, x, y + BASELINE - 20, ICON_SIZE, ICON_SIZE);
                x += ICON_STEP;
            }
        }
        return x;
    }
}
