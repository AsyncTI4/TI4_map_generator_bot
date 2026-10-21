package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import ti4.game.Player;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.Strokes;

final class PlayerArea {

    private static final int STRIP_OFFSET_FROM_SUPPLY = 600;
    private static final int STRIP_ABOVE_ICON = 30;
    private static final int SECOND_ROW_BELOW_STRIP = 160;
    private static final int SECRETS_BELOW_STRIP = 170;
    private static final int AREA_HEIGHT_BELOW_ICON = 85 + 200;
    private static final int SECRETS_WITHOUT_EXTRA_HEIGHT = 4;

    private PlayerArea() {}

    static boolean isSkipped(PlayerAreaContext ctx, Player player) {
        return (player.isDummy() && player.isNpc())
                || ctx.hidesStatsOf(player)
                || ("neutral".equals(player.getFaction()) && player.isDummy());
    }

    static PlayerAreaFrame draw(PlayerAreaContext ctx, AreaSlot slot, Player player, Point topLeft) {
        if (isSkipped(ctx, player)) {
            return PlayerAreaFrame.unframed(new Rectangle(topLeft));
        }
        ctx.warnings().drawn(player);
        Graphics2D g2 = ctx.g2();
        int x = topLeft.x;
        int y = topLeft.y;
        Color mainColor = ColorUtil.getPlayerMainColor(player);
        Color accentColor = ColorUtil.getPlayerAccentColor(player);

        y += PlayerAreaHeader.draw(ctx, slot, player, x, y);
        if (player.getFaction() == null || "null".equals(player.getColor()) || player.getColor() == null) {
            y += 2;
            return PlayerAreaFrame.unframed(new Rectangle(topLeft.x, topLeft.y, 0, y - topLeft.y));
        }

        y += 2;
        IdentityBlock.drawFactionIcon(ctx, player, x, y);
        y += 4;
        IdentityBlock.drawStrategyCards(ctx, player, x + 90, y);
        IdentityBlock.drawStatus(ctx, player, x, y);

        g2.setStroke(Strokes.STROKE_5);
        if (player.isEliminated()) {
            y += IdentityBlock.ICON_SIZE;
            Rectangle bounds = new Rectangle(topLeft.x - 5, topLeft.y, slot.rightEdge() - topLeft.x, y - topLeft.y);
            return new PlayerAreaFrame(bounds, mainColor, accentColor, Strokes.STROKE_5);
        }

        FollowAndNeighboursLine.draw(ctx, player, PlayerAreaHeader.showsNeighbours(ctx.game(), player), x, y);
        SupplyBlock.draw(ctx, player, x, y);

        int stripX = x + SupplyBlock.OFFSET_FROM_ICON + STRIP_OFFSET_FROM_SUPPLY;
        int stripY = y - STRIP_ABOVE_ICON;
        int secondRowY = stripY + SECOND_ROW_BELOW_STRIP;
        y += AREA_HEIGHT_BELOW_ICON;

        int secretCount = ScoredSecrets.draw(ctx, slot, player, stripY + SECRETS_BELOW_STRIP);
        PlanetRow.draw(ctx, player, stripX, secondRowY);
        RightBlocks.draw(ctx, slot, player, stripY, secondRowY);
        CardStrip.draw(ctx, player, stripX, stripY);

        if (secretCount >= SECRETS_WITHOUT_EXTRA_HEIGHT) {
            y += 23 + (secretCount - SECRETS_WITHOUT_EXTRA_HEIGHT) * 43;
        }
        Rectangle bounds = new Rectangle(topLeft.x, topLeft.y, slot.rightEdge() - topLeft.x - 5, y - topLeft.y);
        return new PlayerAreaFrame(bounds, mainColor, accentColor, Strokes.STROKE_2);
    }
}
