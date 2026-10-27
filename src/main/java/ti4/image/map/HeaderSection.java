package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import ti4.game.Game;
import ti4.helpers.DateTimeHelper;
import ti4.image.io.Fonts;

final class HeaderSection {

    private static final int GAME_INFO_TOP_MARGIN = 60;
    private static final int GHOST_HOME_SYSTEM_CLEARANCE = 40;
    private static final int WRAPPED_ROW_LEFT = 20;
    private static final int LABEL_RIGHT_MARGIN = 100;

    private HeaderSection() {}

    static int draw(RenderContext ctx, Graphics2D g2, int landscapeShift) {
        Game game = ctx.game();
        MapCanvasLayout canvas = ctx.canvas();
        g2.setFont(Fonts.ti(32));
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

        g2.setFont(Fonts.ti(50));
        g2.setColor(Color.WHITE);
        g2.drawString(game.getCustomName(), landscapeShift + 4 * ServerIcon.SIZE / 3, y);
        int nameEnd = g2.getFontMetrics().stringWidth(game.getCustomName()) + 5 * ServerIcon.SIZE / 3;

        List<StrategyCardRow.Label> labels = StrategyCardRow.labels(ctx, g2);

        g2.setColor(Color.WHITE);
        g2.setFont(Fonts.ti(64));
        String roundString = "ROUND: " + game.getRound();
        int roundLen = g2.getFontMetrics().stringWidth(roundString);

        TurnOrderTracker.Order turnOrder = TurnOrderTracker.order(ctx);
        HeaderLayout.Placement placement = HeaderLayout.place(new HeaderLayout.Inputs(
                x,
                landscapeShift + WRAPPED_ROW_LEFT,
                y,
                nameEnd,
                labels.stream().map(StrategyCardRow.Label::width).toList(),
                stripEdge(canvas) - LABEL_RIGHT_MARGIN,
                roundLen,
                stripEdge(canvas) - roundLen - 100 * game.getRealPlayers().size(),
                TurnOrderTracker.width(turnOrder),
                CardDeckRow.width(game),
                stripEdge(canvas) - HeaderLayout.RIGHT_MARGIN,
                ExpeditionTracker.isShown(game),
                stripEdge(canvas) - MapCanvasLayout.EXPEDITION_TRACKER_RIGHT_OFFSET,
                stripEdge(canvas) - HeaderLayout.TRACKER_SIZE - HeaderLayout.RIGHT_MARGIN));

        StrategyCardRow.draw(ctx, g2, labels, placement.labelRows());

        g2.setColor(Color.WHITE);
        g2.setFont(Fonts.ti(64));
        g2.drawString(roundString, placement.round().x, placement.round().y);

        CardDeckRow.draw(ctx, g2, placement.decks().x, placement.decks().y);

        TurnOrderTracker.draw(ctx, g2, turnOrder, placement.turnOrder().x, placement.turnOrder().y);

        if (placement.tracker() != null) {
            ExpeditionTracker.draw(ctx, g2, placement.tracker().x, placement.tracker().y);
        }

        return placement.bottom();
    }

    private static int stripEdge(MapCanvasLayout canvas) {
        return canvas.isLandscape() ? canvas.landscapeRightEdge() : canvas.stripWidth();
    }
}
