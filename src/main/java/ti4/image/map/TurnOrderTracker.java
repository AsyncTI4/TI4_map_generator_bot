package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.io.Fonts;

final class TurnOrderTracker {

    private static final ColorConvertOp GRAYSCALE_CONVERT_OP =
            new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
    private static final int ICON_STEP = 100;
    private static final int LABELS_WIDTH = 190;

    record Icon(Player player, BufferedImage image) {}

    record Order(List<Icon> upNext, List<Icon> passed) {

        int width() {
            int icons = ICON_STEP * upNext.size();
            if (!passed.isEmpty()) {
                icons += ICON_STEP + ICON_STEP * passed.size();
            }
            return Math.max(icons, LABELS_WIDTH);
        }
    }

    private TurnOrderTracker() {}

    @Nullable
    static Order order(RenderContext ctx) {
        Game game = ctx.game();
        String activePlayerUserID = game.getActivePlayerID();
        if (ctx.fog().isPrivate() || activePlayerUserID == null || !"action".equals(game.getPhaseOfGame())) {
            return null;
        }
        Player activePlayer = game.getPlayer(activePlayerUserID);
        List<Player> allPlayers = new ArrayList<>(game.getRealPlayers());
        allPlayers.sort(Player.comparingInitiative());
        Collections.rotate(allPlayers, allPlayers.size() - allPlayers.indexOf(activePlayer));

        List<Icon> upNext = new ArrayList<>();
        for (Player player : allPlayers) {
            if (player.isPassed() || player.getSCs().isEmpty()) continue;
            addIcon(upNext, player);
        }
        List<Icon> passed = new ArrayList<>();
        for (Player player : game.getPassedPlayers()) {
            addIcon(passed, player);
        }
        return new Order(upNext, passed);
    }

    private static void addIcon(List<Icon> icons, Player player) {
        if (player.getFaction() == null) return;
        BufferedImage image = FactionIcons.of(player);
        if (image != null) {
            icons.add(new Icon(player, image));
        }
    }

    static int width(@Nullable Order order) {
        return order == null ? 0 : order.width();
    }

    static void draw(RenderContext ctx, Graphics2D g2, @Nullable Order order, int x, int y) {
        if (order == null) {
            return;
        }
        g2.setFont(Fonts.ti(20));
        g2.setColor(ColorUtil.ActiveColor);
        g2.drawString("ACTIVE", x + 10, y + 35);
        g2.setFont(Fonts.ti(16));
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawString("NEXT UP", x + 112, y + 34);

        for (Icon icon : order.upNext()) {
            g2.drawImage(icon.image(), x, y - 70, null);
            if (!icon.player().hasCustomFactionEmoji()) {
                ctx.overlays().add(icon.player().getFactionModel(), x + 10, y - 60, 75, 75);
            }
            x += ICON_STEP;
        }
        x += ICON_STEP;
        for (Icon icon : order.passed()) {
            BufferedImage grayscale = GRAYSCALE_CONVERT_OP.filter(icon.image(), null);
            g2.drawImage(grayscale, x, y - 70, null);
            g2.setColor(Color.RED);
            g2.drawString("PASSED", x + 10, y + 34);
            x += ICON_STEP;
        }
    }
}
