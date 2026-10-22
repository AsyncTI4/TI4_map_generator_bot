package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PlayerTokens;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.model.Mapper;

final class PriorityTrackSection {

    private static final int BOX_HEIGHT = 95;
    private static final int BOX_WIDTH = 100;
    private static final int BOX_BUFFER = -1;
    private static final int LABEL_WIDTH = 360;
    private static final float TOKEN_SCALE = 0.7f;

    private PriorityTrackSection() {}

    static boolean isShown(Game game) {
        return game.hasAnyPriorityTrackMode();
    }

    static int draw(RenderContext ctx, Graphics2D g2, int x, int y) {
        Game game = ctx.game();
        String trackName = game.isOmegaPhaseMode() ? "PRIORITY:" : "SPEAKER:";
        g2.setStroke(Strokes.STROKE_5);
        g2.setFont(Fonts.ti(50));
        var priorityTrack = PriorityTrackHelper.getPriorityTrack(game);
        OutlinedText.drawCentered(g2, trackName, new Rectangle(x, y, LABEL_WIDTH, BOX_HEIGHT), Fonts.ti(64));
        int trackXShift = x + LABEL_WIDTH;
        for (int i = 0; i < priorityTrack.size(); i++) {
            g2.setColor(Color.WHITE);
            Rectangle rect = new Rectangle(i * BOX_WIDTH + trackXShift, y, BOX_WIDTH, BOX_HEIGHT / 2);
            OutlinedText.drawCentered(g2, Integer.toString(i + 1), rect, Fonts.ti(50));
            g2.setColor(ColorUtil.getSCColor(6, game, true));
            g2.drawRect(i * BOX_WIDTH + trackXShift, y, BOX_WIDTH, BOX_HEIGHT);
        }

        int availableSpacePerColumn = BOX_WIDTH - BOX_BUFFER * 2;
        int availableSpacePerRow = BOX_HEIGHT - BOX_BUFFER * 2;
        for (Player player : priorityTrack) {
            if (player == null) {
                continue;
            }
            try {
                String controlID = Mapper.getControlID(player.getColor());

                BufferedImage controlTokenImage =
                        ImageHelper.readScaled(ImageResources.commandToken(controlID), TOKEN_SCALE);
                int tokenWidth = controlTokenImage == null ? 51 : controlTokenImage.getWidth();
                int tokenHeight = controlTokenImage == null ? 33 : controlTokenImage.getHeight();
                int centreHorizontally = Math.max(0, (availableSpacePerColumn - tokenWidth) / 2);
                int lowerVerticalQuarter = Math.max(0, (availableSpacePerRow - tokenHeight) * 3 / 4);

                int spaceOnTrack = player.getPriorityPosition() - 1;
                int boxCenter = BOX_BUFFER + centreHorizontally;
                int boxFarRight = BOX_WIDTH - tokenWidth - BOX_BUFFER;
                int firstX = Math.min(boxCenter, boxFarRight) + trackXShift;
                int tokenX = spaceOnTrack * BOX_WIDTH + firstX;
                int tokenY = y + BOX_BUFFER + lowerVerticalQuarter;
                PlayerTokens.drawControlToken(g2, controlTokenImage, player, tokenX, tokenY, false, TOKEN_SCALE);
            } catch (Exception e) {
                BotLogger.error("Could not display player: " + player.getUserName(), e);
            }
        }
        return y + BOX_HEIGHT + 20;
    }
}
