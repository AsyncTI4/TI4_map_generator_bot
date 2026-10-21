package ti4.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import ti4.game.Game;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;

final class HeaderSection {

    private static final int GAME_INFO_TOP_MARGIN = 60;
    private static final int GHOST_HOME_SYSTEM_CLEARANCE = 40;
    private static final int CARD_DECKS_ABOVE_BASELINE = 75;
    private static final int EXPEDITION_TRACKER_ABOVE_BASELINE = 265;
    private static final int BOTTOM_MARGIN = 30;

    private HeaderSection() {}

    static int draw(RenderContext ctx, Graphics2D g2, int landscapeShift) {
        Game game = ctx.game();
        MapCanvasLayout canvas = ctx.canvas();
        g2.setFont(Storage.getFont32());
        g2.setColor(Color.WHITE);
        String timeStamp = DateTimeHelper.getFormattedTimestamp();
        g2.drawString(game.getName() + " " + game.getCreationDate() + " - " + timeStamp, 0, 34);
        SegmentTitle.draw(ctx, g2);
        int y = canvas.heightForGameInfo() + GAME_INFO_TOP_MARGIN;
        int x = landscapeShift + BoardLayout.PLAYER_AREAS_LEFT;

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        GameModeBadges.draw(ctx, g2, x, y);

        y += GHOST_HOME_SYSTEM_CLEARANCE;

        ServerIcon.draw(game, g2, x, y);

        g2.setFont(Storage.getFont50());
        g2.setColor(Color.WHITE);
        g2.drawString(game.getCustomName(), landscapeShift + 4 * ServerIcon.SIZE / 3, y);
        int nameEnd = g2.getFontMetrics().stringWidth(game.getCustomName()) + 5 * ServerIcon.SIZE / 3;

        Point coord = StrategyCardRow.draw(ctx, g2, new Point(x, y));
        coord.translate(100, 0);

        g2.setColor(Color.WHITE);
        g2.setFont(Storage.getFont64());
        String roundString = "ROUND: " + game.getRound();
        int roundLen = g2.getFontMetrics().stringWidth(roundString);
        if (coord.x
                > canvas.stripWidth() - roundLen - 100 * game.getRealPlayers().size()) {
            coord = new Point(landscapeShift + 20, coord.y + 100);
        }
        g2.drawString(roundString, coord.x, coord.y);

        CardDeckRow.draw(ctx, g2, Math.max(x + nameEnd, coord.x), y - CARD_DECKS_ABOVE_BASELINE);

        coord = TurnOrderTracker.draw(ctx, g2, coord.x + roundLen + 100 + landscapeShift, coord.y);

        ExpeditionTracker.draw(
                ctx,
                g2,
                canvas.stripWidth() - MapCanvasLayout.EXPEDITION_TRACKER_RIGHT_OFFSET,
                coord.y - EXPEDITION_TRACKER_ABOVE_BASELINE);

        return coord.y + BOTTOM_MARGIN;
    }
}
