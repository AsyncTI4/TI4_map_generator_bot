package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.ListUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.RenderRandom;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PlayerTokens;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.model.Mapper;

final class ScoreTrackSection {

    private static final int BOX_HEIGHT = 140;
    private static final int BOX_BUFFER = -1;
    private static final int TRACK_HEIGHT_WITH_MARGIN = 160;
    private static final float TOKEN_SCALE = 0.7f;

    private ScoreTrackSection() {}

    static int width(Game game) {
        int boxCount = 1 + Math.max(game.getVp(), Math.max(pinkLimit(game), greyLimit(game)));
        return boxCount * boxWidth(game) + ObjectivesSection.SPACING_BETWEEN_OBJECTIVE_TYPES;
    }

    static int draw(RenderContext ctx, Graphics2D g2, int x, int y) {
        Game game = ctx.game();
        FogView fog = ctx.fog();
        g2.setStroke(Strokes.STROKE_5);
        g2.setFont(Fonts.ti(50));

        int pinkLimit = pinkLimit(game);
        int greyLimit = greyLimit(game);
        int boxWidth = boxWidth(game);

        for (int i = 1 + Math.max(game.getVp(), pinkLimit); i <= greyLimit; i++) {
            g2.setColor(Color.WHITE);
            Rectangle rect = new Rectangle(i * boxWidth + x, y, boxWidth, BOX_HEIGHT);
            OutlinedText.drawCentered(g2, Integer.toString(i), rect, Fonts.ti(50));
            g2.setColor(Color.GRAY);
            g2.drawRect(i * boxWidth + x, y, boxWidth, BOX_HEIGHT);
        }

        for (int i = game.getVp() + 1; i <= pinkLimit; i++) {
            g2.setColor(Color.WHITE);
            Rectangle rect = new Rectangle(i * boxWidth + x, y, boxWidth, BOX_HEIGHT);
            OutlinedText.drawCentered(g2, Integer.toString(i), rect, Fonts.ti(50));
            g2.setColor(Color.PINK);
            g2.drawRect(i * boxWidth + x, y, boxWidth, BOX_HEIGHT);
        }

        for (int i = 0; i <= game.getVp(); i++) {
            g2.setColor(Color.WHITE);
            Rectangle rect = new Rectangle(i * boxWidth + x, y, boxWidth, BOX_HEIGHT);
            OutlinedText.drawCentered(g2, Integer.toString(i), rect, Fonts.ti(50));
            g2.setColor(Color.RED);
            g2.drawRect(i * boxWidth + x, y, boxWidth, BOX_HEIGHT);
        }

        List<Player> players = new ArrayList<>(game.getRealPlayers());
        if (fog.isPrivate()) {
            RenderRandom.shuffle("scoreTrack.fogShuffle", players);
        }

        int row = 0;
        int col = 0;
        int playerCount = players.size();
        int rowCount = (int) Math.max(2, Math.ceil(Math.sqrt(1.0 + playerCount) + 0.1));
        List<List<Player>> playerChunks = ListUtils.partition(players, rowCount);
        int colCount = (int) Math.max(2, Math.ceil(1.0 * playerCount / rowCount));
        int availableSpacePerColumn = (boxWidth - BOX_BUFFER * 2) / colCount;
        int availableSpacePerRow = (BOX_HEIGHT - BOX_BUFFER * 2) / rowCount;
        for (List<Player> playerChunk : playerChunks) {
            for (Player player : playerChunk) {
                try {
                    boolean convertToGeneric = fog.shouldConvertToGeneric(player);
                    String controlID =
                            convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());

                    BufferedImage controlTokenImage =
                            ImageHelper.readScaled(ImageResources.commandToken(controlID), TOKEN_SCALE);
                    int tokenWidth = controlTokenImage == null ? 51 : controlTokenImage.getWidth();
                    int tokenHeight = controlTokenImage == null ? 33 : controlTokenImage.getHeight();
                    int centreHorizontally = Math.max(0, (availableSpacePerColumn - tokenWidth) / 2);
                    int centreVertically = Math.max(0, (availableSpacePerRow - tokenHeight) / 2);

                    int vpCount = player.getTotalVictoryPoints();
                    int tokenX = vpCount * boxWidth
                            + Math.min(
                                    BOX_BUFFER + (availableSpacePerColumn * col) + centreHorizontally,
                                    boxWidth - tokenWidth - BOX_BUFFER)
                            + x;
                    int tokenY = y + BOX_BUFFER + (availableSpacePerRow * row) + centreVertically;
                    PlayerTokens.drawControlToken(
                            g2,
                            controlTokenImage,
                            PlayerTokens.ownerOfToken(game.getPlayers().values(), controlID),
                            tokenX,
                            tokenY,
                            convertToGeneric,
                            TOKEN_SCALE);
                } catch (Exception e) {
                    BotLogger.error("Could not display player: " + player.getUserName(), e);
                }
                row++;
            }
            row = 0;
            col++;
        }
        return y + TRACK_HEIGHT_WITH_MARGIN;
    }

    private static int pinkLimit(Game game) {
        if (game.isAllianceMode()) return 14;
        if (game.isLiberationC4Mode()) return 12;
        return 0;
    }

    private static int greyLimit(Game game) {
        return game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
    }

    private static int boxWidth(Game game) {
        int greyLimit = greyLimit(game);
        if (game.getVp() > 14 || greyLimit > 14) {
            return 2250 / (1 + Math.max(game.getVp(), Math.max(pinkLimit(game), greyLimit)));
        }
        return 150;
    }
}
