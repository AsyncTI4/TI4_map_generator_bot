package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.ShapeDrawing;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;
import ti4.model.StrategyCardModel;

final class IdentityBlock {

    static final int ICON_SIZE = 95;

    private IdentityBlock() {}

    static void drawFactionIcon(PlayerAreaContext ctx, Player player, int x, int y) {
        FactionIcons.draw(ctx.g2(), player, x, y, ICON_SIZE, ICON_SIZE);
        if (!player.hasCustomFactionEmoji()) {
            ctx.overlays().add(player.getFactionModel(), x + 10, y + 10, 75, 75);
        }
    }

    static void drawStrategyCards(PlayerAreaContext ctx, Player player, int x, int y) {
        List<Integer> playerSCs = new ArrayList<>(player.getSCs());
        Collections.sort(playerSCs);

        Point center = new Point(x + 30, y + 42);
        if (player.hasTheZeroToken()) {
            drawStrategyCard(ctx, 0, center, true);
        }
        if (player.hasAbility("patience")) {
            drawStrategyCard(ctx, 9, center, true);
        }

        boolean big = playerSCs.size() == 1 && !player.hasTheZeroToken() && !player.hasAbility("patience");
        List<Point> smallPoints = List.of(
                new Point(center.x - 16, center.y - 16),
                new Point(center.x + 16, center.y - 16),
                new Point(center.x - 16, center.y + 16),
                new Point(center.x + 16, center.y + 16));
        int count = 0;
        for (int sc : playerSCs) {
            drawStrategyCard(ctx, sc, big ? center : smallPoints.get(count), big);
            ++count;
            if (count > 3) break;
        }
    }

    private static void drawStrategyCard(PlayerAreaContext ctx, int sc, Point pt, boolean big) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        Color scColor = ColorUtil.getSCColor(sc, game);
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        g2.setFont(big ? Fonts.ti(64) : Fonts.ti(32));

        if (sc == ButtonHelper.getKyroHeroSC(game)) sc = 9;
        OutlinedText.drawCentered(g2, Integer.toString(sc), pt.x, pt.y, scColor, Strokes.STROKE_4, Color.black);
        if (Color.GRAY.equals(scColor)) {
            int off = big ? 15 : 8;
            ShapeDrawing.redX(g2, pt.x + off, pt.y + off, 2 * off, big);
        }
        if (scModel != null) {
            int overlaySize = big ? 50 : 30;
            ctx.overlays().add(scModel, pt.x, pt.y, overlaySize / 2, overlaySize);
        }
    }

    static void drawStatus(PlayerAreaContext ctx, Player player, int x, int y) {
        String label = statusLabel(ctx.game(), player);
        Graphics2D g2 = ctx.g2();
        AffineTransform transform = g2.getTransform();
        g2.translate(x + 47 - 3, y + 47 - 6);
        g2.rotate(-Math.PI / 4);
        g2.setFont(Fonts.ti(20));
        if (label != null) {
            Color color = "PASSED".equals(label)
                    ? ColorUtil.PassedColor
                    : "ACTIVE".equals(label) ? ColorUtil.ActiveColor : ColorUtil.EliminatedColor;
            OutlinedText.drawCentered(g2, label, 0, 0, color, Strokes.STROKE_4, Color.BLACK);
        }
        g2.setTransform(transform);
    }

    private static String statusLabel(Game game, Player player) {
        if (player.isEliminated()) return "ELIMINATED";
        if (player.isDummy()) return "DUMMY";
        if (player.isNpc()) return "NPC";
        if (player.isPassed()) return "PASSED";
        if (player.getUserID().equals(game.getActivePlayerID()) && "action".equals(game.getPhaseOfGame())) {
            return "ACTIVE";
        }
        return null;
    }
}
