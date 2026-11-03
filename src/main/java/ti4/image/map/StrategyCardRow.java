package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.ColorUtil;
import ti4.image.io.Fonts;

final class StrategyCardRow {

    private static final int OVERLAY_ABOVE_BASELINE = 56;

    record Label(int sc, int tradeGoods, int width) {}

    private StrategyCardRow() {}

    static void draw(RenderContext ctx, Graphics2D g2, List<Label> labels, List<HeaderLayout.LabelRow> rows) {
        for (HeaderLayout.LabelRow row : rows) {
            int x = row.x();
            for (Label label : labels.subList(row.first(), row.end())) {
                drawLabel(ctx, g2, label, x, row.baseline());
                x += label.width() + HeaderLayout.LABEL_SPACING;
            }
        }
    }

    static List<Label> labels(RenderContext ctx, Graphics2D g2) {
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
            g2.setFont(Fonts.ti(64));
            int width = g2.getFontMetrics().stringWidth(Integer.toString(sc));
            if (tradeGoods > 0) {
                g2.setFont(Fonts.ti(24));
                width = Math.max(width, g2.getFontMetrics().stringWidth("TG:" + tradeGoods));
            }
            labels.add(new Label(sc, tradeGoods, width));
        }
        return labels;
    }

    private static void drawLabel(RenderContext ctx, Graphics2D g2, Label label, int x, int baseline) {
        Game game = ctx.game();
        boolean fogPrivate = ctx.fog().isPrivate();
        int sc = label.sc();
        String scText = Integer.toString(sc);
        g2.setFont(Fonts.ti(64));
        int textWidth = g2.getFontMetrics().stringWidth(scText);
        boolean scPicked = game.getPlayers().values().stream()
                .anyMatch(player -> player.getSCs().contains(sc));

        if (!fogPrivate && !scPicked) {
            g2.setColor(ColorUtil.getSCColor(sc, game));
            if (!game.getStoredValue("exhaustedSC" + sc).isEmpty()) {
                g2.setColor(Color.GRAY);
            }
            g2.drawString(scText, x, baseline);
            ctx.overlays()
                    .add(
                            game.getStrategyCardModelByInitiative(sc).orElse(null),
                            x,
                            baseline - OVERLAY_ABOVE_BASELINE,
                            textWidth,
                            60);
            if (label.tradeGoods() > 0) {
                g2.setFont(Fonts.ti(24));
                g2.setColor(Color.WHITE);
                String tgMsg = "TG:" + label.tradeGoods();
                int tgMsgTextWidth = g2.getFontMetrics().stringWidth(tgMsg);
                g2.drawString(tgMsg, x + textWidth / 2 - tgMsgTextWidth / 2, baseline + 30);
            }
        }
        if (fogPrivate && game.getScPlayed().getOrDefault(sc, false)) {
            g2.setColor(Color.GRAY);
            g2.setFont(Fonts.ti(64));
            g2.drawString(scText, x, baseline);
        }
    }
}
