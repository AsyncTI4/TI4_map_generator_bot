package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import ti4.game.Player;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PlayerTokens;
import ti4.image.draw.ShapeDrawing;
import ti4.image.io.Fonts;
import ti4.model.Mapper;

final class PlotCards {

    private static final int HEIGHT = 150;
    private static final int COLUMN_WIDTH = 180;
    private static final int ROWS_PER_COLUMN = 5;
    private static final int ROW_HEIGHT = 27;

    private PlotCards() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (!player.hasAbility("bladesorchestra") && !player.hasAbility("plotsplots")) {
            return xDeltaFromRight;
        }
        boolean faceup = player.hasAbility("bladesorchestra");
        List<Entry<String, Integer>> plots =
                new ArrayList<>(player.getPlotCardsRaw().entrySet());
        plots.sort(Entry.comparingByValue());
        if (faceup) plots.sort(Entry.comparingByKey());

        int columnCount = Math.max(1, (plots.size() + ROWS_PER_COLUMN - 1) / ROWS_PER_COLUMN);
        int width = COLUMN_WIDTH * columnCount;
        xDeltaFromRight += width + 15;

        Graphics2D g2 = ctx.g2();
        int left = slot.rightEdge() - xDeltaFromRight;
        Color main = ColorUtil.getPlayerMainColor(player);
        Color accent = ColorUtil.getPlayerAccentColor(player);
        ShapeDrawing.gradientOutline(g2, main, accent, left, y, width, HEIGHT);

        g2.setColor(Color.white);
        g2.setFont(Fonts.ti(20));
        int firstRowY = y + 28;
        for (int i = 0; i < plots.size(); i++) {
            int column = i / ROWS_PER_COLUMN;
            int row = i % ROWS_PER_COLUMN;
            int x = left + 8 + (columnCount - 1 - column) * COLUMN_WIDTH;
            drawPlot(ctx, player, plots.get(i), faceup, x, firstRowY + row * ROW_HEIGHT);
        }
        return xDeltaFromRight;
    }

    private static void drawPlot(
            PlayerAreaContext ctx, Player player, Entry<String, Integer> plot, boolean faceup, int x, int plotY) {
        Graphics2D g2 = ctx.g2();
        String alias = plot.getKey();
        String name = faceup ? Mapper.getPlot(alias).getName() : "Plot " + plot.getValue();
        boolean mutated = name.contains("Mutated ");
        if (mutated) name = name.replace("Mutated ", "");

        OutlinedText.draw(g2, name, x, plotY, Color.white, HorizontalAlign.Left, null, null, null);
        int strWidth = g2.getFontMetrics().stringWidth(name);
        if (mutated && faceup) {
            g2.setColor(Color.red);
            g2.drawLine(x - 2, plotY - 7, x + strWidth + 2, plotY - 7);
            g2.setColor(Color.white);
        }

        x += 95;
        for (String faction : player.getPlotCardsFactionsRaw().getOrDefault(alias, List.of())) {
            Player target = ctx.game().getPlayerFromColorOrFaction(faction);
            PlayerTokens.readAndDrawControlToken(g2, target, x, plotY - 20, ctx.fogPrivate(), 0.6f);
            x += 30;
        }
    }
}
