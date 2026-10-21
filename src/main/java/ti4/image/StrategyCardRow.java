package ti4.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Storage;

final class StrategyCardRow {

    private static final int LABEL_SPACING = 25;
    private static final int WRAPPED_ROW_LEFT = 20;
    private static final int WRAPPED_ROW_STEP = 100;
    private static final int RIGHT_MARGIN = 100;

    private record Label(int sc, int tradeGoods, int width) {}

    private StrategyCardRow() {}

    static Point draw(RenderContext ctx, Graphics2D g2, Point coord) {
        List<Label> labels = labels(ctx, g2);
        int rowStart = coord.x;
        int maxRowWidth = rightLimit(ctx) - rowStart + LABEL_SPACING;
        List<List<Label>> rows = PlayerAreaGenerator.isMegaGame(ctx.game())
                ? RowWrap.balancedRows(labels, label -> label.width() + LABEL_SPACING, 0, maxRowWidth)
                : oneStrip(ctx, labels, rowStart);

        int x = rowStart;
        int deltaY = coord.y + 80;
        for (int row = 0; row < rows.size(); row++) {
            if (row > 0) {
                x = WRAPPED_ROW_LEFT + ctx.canvas().landscapeShift();
                deltaY += WRAPPED_ROW_STEP;
            }
            for (Label label : rows.get(row)) {
                drawLabel(ctx, g2, label, x, coord.y, deltaY);
                x += label.width() + LABEL_SPACING;
            }
        }
        return new Point(x, deltaY);
    }

    private static List<List<Label>> oneStrip(RenderContext ctx, List<Label> labels, int rowStart) {
        MapCanvasLayout canvas = ctx.canvas();
        int rightLimit = (canvas.isLandscape() ? canvas.landscapeRightEdge() : canvas.stripWidth()) - RIGHT_MARGIN;
        List<List<Label>> rows = new ArrayList<>();
        rows.add(new ArrayList<>());
        int x = rowStart;
        for (Label label : labels) {
            rows.getLast().add(label);
            x += label.width() + LABEL_SPACING;
            if (x > rightLimit) {
                x = WRAPPED_ROW_LEFT + canvas.landscapeShift();
                rows.add(new ArrayList<>());
            }
        }
        return rows;
    }

    private static List<Label> labels(RenderContext ctx, Graphics2D g2) {
        Game game = ctx.game();
        Set<Integer> scPicked = new HashSet<>();
        for (Player player : game.getPlayers().values()) {
            scPicked.addAll(player.getSCs());
        }
        List<Label> labels = new ArrayList<>();
        for (Map.Entry<Integer, Integer> scTGs : game.getScTradeGoods().entrySet()) {
            int sc = scTGs.getKey();
            if (sc == 0) {
                continue;
            }
            int tradeGoods = ctx.fog().isPrivate() || scPicked.contains(sc) ? 0 : scTGs.getValue();
            g2.setFont(Storage.getFont64());
            int width = g2.getFontMetrics().stringWidth(Integer.toString(sc));
            if (tradeGoods > 0) {
                g2.setFont(Storage.getFont24());
                width = Math.max(width, g2.getFontMetrics().stringWidth("TG:" + tradeGoods));
            }
            labels.add(new Label(sc, tradeGoods, width));
        }
        return labels;
    }

    private static int rightLimit(RenderContext ctx) {
        MapCanvasLayout canvas = ctx.canvas();
        if (canvas.isLandscape()) {
            return canvas.landscapeRightEdge() - RIGHT_MARGIN;
        }
        if (ExpeditionTracker.isShown(ctx.game())) {
            return canvas.stripWidth() - MapCanvasLayout.EXPEDITION_TRACKER_RIGHT_OFFSET - LABEL_SPACING;
        }
        return canvas.stripWidth() - RIGHT_MARGIN;
    }

    private static void drawLabel(RenderContext ctx, Graphics2D g2, Label label, int x, int y, int baseline) {
        Game game = ctx.game();
        boolean fogPrivate = ctx.fog().isPrivate();
        int sc = label.sc();
        String scText = Integer.toString(sc);
        g2.setFont(Storage.getFont64());
        int textWidth = g2.getFontMetrics().stringWidth(scText);
        boolean scPicked = game.getPlayers().values().stream()
                .anyMatch(player -> player.getSCs().contains(sc));

        if (!fogPrivate && !scPicked) {
            g2.setColor(ColorUtil.getSCColor(sc, game));
            if (!game.getStoredValue("exhaustedSC" + sc).isEmpty()) {
                g2.setColor(Color.GRAY);
            }
            g2.drawString(scText, x, baseline);
            ctx.overlays().add(game.getStrategyCardModelByInitiative(sc).orElse(null), x, y + 24, textWidth, 60);
            if (label.tradeGoods() > 0) {
                g2.setFont(Storage.getFont24());
                g2.setColor(Color.WHITE);
                String tgMsg = "TG:" + label.tradeGoods();
                int tgMsgTextWidth = g2.getFontMetrics().stringWidth(tgMsg);
                g2.drawString(tgMsg, x + textWidth / 2 - tgMsgTextWidth / 2, baseline + 30);
            }
        }
        if (fogPrivate && game.getScPlayed().getOrDefault(sc, false)) {
            g2.setColor(Color.GRAY);
            g2.setFont(Storage.getFont64());
            g2.drawString(scText, x, baseline);
        }
    }
}
