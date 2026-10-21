package ti4.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Storage;

final class TurnOrderTracker {

    private static final ColorConvertOp GRAYSCALE_CONVERT_OP =
            new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

    private TurnOrderTracker() {}

    static Point draw(RenderContext ctx, Graphics2D g2, int x, int y) {
        Game game = ctx.game();
        boolean convertToGenericSC = ctx.fog().isPrivate();
        String activePlayerUserID = game.getActivePlayerID();
        if (!convertToGenericSC && activePlayerUserID != null && "action".equals(game.getPhaseOfGame())) {
            g2.setFont(Storage.getFont20());
            g2.setColor(ColorUtil.ActiveColor);
            g2.drawString("ACTIVE", x + 10, y + 35);
            g2.setFont(Storage.getFont16());
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("NEXT UP", x + 112, y + 34);

            Player activePlayer = game.getPlayer(activePlayerUserID);
            List<Player> allPlayers = new ArrayList<>(game.getRealPlayers());
            allPlayers.sort(Player.comparingInitiative());

            int rotationDistance = allPlayers.size() - allPlayers.indexOf(activePlayer);
            Collections.rotate(allPlayers, rotationDistance);
            for (Player player : allPlayers) {
                if (player.isPassed() || player.getSCs().isEmpty()) continue;
                String faction = player.getFaction();
                if (faction != null) {
                    BufferedImage bufferedImage = DrawingUtil.getPlayerFactionIconImage(player);
                    if (bufferedImage != null) {
                        g2.drawImage(bufferedImage, x, y - 70, null);
                        if (!player.hasCustomFactionEmoji()) {
                            ctx.overlays().add(player.getFactionModel(), x + 10, y - 60, 75, 75);
                        }
                        x += 100;
                    }
                }
            }
            x += 100;
            for (Player player : game.getPassedPlayers()) {
                String faction = player.getFaction();
                if (faction != null) {
                    BufferedImage bufferedImage = DrawingUtil.getPlayerFactionIconImage(player);
                    if (bufferedImage != null) {
                        bufferedImage = GRAYSCALE_CONVERT_OP.filter(bufferedImage, null);
                        g2.drawImage(bufferedImage, x, y - 70, null);
                        g2.setColor(Color.RED);
                        g2.drawString("PASSED", x + 10, y + 34);
                        x += 100;
                    }
                }
            }
        }
        return new Point(x, y);
    }
}
