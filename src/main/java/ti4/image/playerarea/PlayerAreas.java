package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.io.Fonts;

public final class PlayerAreas {

    private static final int AREA_GAP = 15;
    private static final int SPECTATORS_GAP = 15;

    private PlayerAreas() {}

    public static int drawAll(PlayerAreaContext ctx, Point topLeft, int totalWidth) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        g2.setFont(Fonts.ti(32));
        int x = topLeft.x;
        List<Player> playersWithAreas = game.getRealAndEliminatedPlayers().stream()
                .filter(player -> !PlayerArea.isSkipped(ctx, player))
                .toList();
        List<List<Player>> columns = PlayerAreaColumns.split(game, playersWithAreas);
        int columnWidth = PlayerAreaColumns.columnWidth(totalWidth, x, columns.size());

        int y = topLeft.y;
        int rowCount = columns.getFirst().size();
        for (int row = 0; row < rowCount; row++) {
            List<PlayerAreaFrame> frames = new ArrayList<>();
            for (int column = 0;
                    column < columns.size() && row < columns.get(column).size();
                    column++) {
                int columnLeft = x + column * (columnWidth + PlayerAreaColumns.COLUMN_GAP);
                int rightEdge = column == columns.size() - 1 ? totalWidth : columnLeft + columnWidth;
                AreaSlot slot = new AreaSlot(columnLeft, rightEdge);
                frames.add(PlayerArea.draw(ctx, slot, columns.get(column).get(row), new Point(columnLeft, y)));
            }
            int rowHeight = frames.stream()
                    .mapToInt(frame -> frame.bounds().height)
                    .max()
                    .orElse(0);
            frames.forEach(frame -> frame.draw(g2, rowHeight));
            if (rowHeight > 0) y += rowHeight + AREA_GAP;
        }
        return drawSpectators(ctx, x, y, totalWidth);
    }

    public static Rectangle drawSingle(PlayerAreaContext ctx, Player player, Point topLeft, int totalWidth) {
        PlayerAreaFrame frame = PlayerArea.draw(ctx, new AreaSlot(topLeft.x, totalWidth), player, topLeft);
        frame.draw(ctx.g2(), frame.bounds().height);
        return frame.bounds();
    }

    private static int drawSpectators(PlayerAreaContext ctx, int x, int y, int totalWidth) {
        String spectatorNames = ctx.game().getPlayers().values().stream()
                .filter(Player::isSpectator)
                .map(Player::getUserName)
                .collect(Collectors.joining(", "));
        if (spectatorNames.isEmpty()) {
            return y;
        }
        Graphics2D g2 = ctx.g2();
        g2.setFont(Fonts.ti(32));
        g2.setColor(Color.WHITE);
        return drawWrapped(g2, "Spectators: " + spectatorNames, x, y + SPECTATORS_GAP, totalWidth - x);
    }

    private static int drawWrapped(Graphics2D g2, String text, int x, int y, float maxWidth) {
        AttributedString attributedString = new AttributedString(text);
        attributedString.addAttribute(TextAttribute.FONT, g2.getFont());
        AttributedCharacterIterator characterIterator = attributedString.getIterator();
        LineBreakMeasurer lineBreakMeasurer = new LineBreakMeasurer(characterIterator, g2.getFontRenderContext());

        float drawPosY = y;
        while (lineBreakMeasurer.getPosition() < characterIterator.getEndIndex()) {
            TextLayout layout = lineBreakMeasurer.nextLayout(maxWidth);
            drawPosY += layout.getAscent();
            layout.draw(g2, x, drawPosY);
            drawPosY += layout.getDescent() + layout.getLeading();
        }
        return (int) Math.ceil(drawPosY);
    }
}
