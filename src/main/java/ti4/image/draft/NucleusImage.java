package ti4.image.draft;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.io.Fonts;
import ti4.image.io.RemoteImages;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;
import ti4.service.emoji.TI4Emoji;
import ti4.service.milty.MiltyDraftSlice;

public final class NucleusImage {

    private static final DraftBoard BOARD = new DraftBoard(0, 0);
    private static final int EMOJI_SIZE = 40;
    private static final int EMOJI_MARGIN = 10;
    private static final int PLAYER_NAME_Y = 220;

    private NucleusImage() {}

    public record PlayerInfo(String userName, List<TI4Emoji> summaryEmojis) {}

    @Nullable
    public static BufferedImage render(
            Game game,
            Map<String, MiltyDraftSlice> slicesBySeatPosition,
            Map<String, PlayerInfo> playersBySeatPosition) {
        BufferedImage image = new BufferedImage(BOARD.width(game), BOARD.height(game), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Map<String, Tile> tiles = DraftBoard.tilesOnBoard(game);
        Map<String, Point> tilePoints = new HashMap<>();

        for (Tile tile : tiles.values()) {
            try {
                Point tilePosition = BOARD.tilePosition(game, tile.getPosition());
                TileLayers tileLayers = TileLayers.of(game, null, DisplayType.map);
                BufferedImage tileImage = tileLayers.draw(tile, TileStep.Nucleus);
                graphics.drawImage(tileImage, tilePosition.x, tilePosition.y, null);
                tilePoints.put(tile.getPosition(), tilePosition);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile IMG " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        for (Tile tile : tiles.values()) {
            try {
                MiltyDraftSlice slice = slicesBySeatPosition.get(tile.getPosition());
                if (slice != null) {
                    drawSliceStats(graphics, tilePoints.get(tile.getPosition()), slice);
                }
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile STATS " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        for (Tile tile : tiles.values()) {
            try {
                PlayerInfo playerInfo = playersBySeatPosition.get(tile.getPosition());
                if (playerInfo != null) {
                    drawPlayerInfo(
                            graphics,
                            tilePoints.get(tile.getPosition()),
                            playerInfo.userName(),
                            playerInfo.summaryEmojis());
                }
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile PLAYER " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        return image;
    }

    private static void drawSliceStats(Graphics2D graphics, Point tilePosition, MiltyDraftSlice slice) {
        Point base = DraftBoard.contentOrigin(tilePosition);
        SliceStats.drawName(graphics, slice.getName(), base);
        SliceStats.drawFeatures(graphics, slice, base);
        SliceStats.drawTotals(graphics, slice, base);
    }

    private static void drawPlayerInfo(
            Graphics2D graphics, Point tilePosition, String userName, List<TI4Emoji> summaryEmojis) {
        if (userName == null || userName.isBlank()) return;

        Point base = DraftBoard.contentOrigin(tilePosition);
        graphics.setColor(Color.white);
        graphics.setFont(Fonts.ti(50));
        SliceStats.drawCenteredLabel(graphics, userName, Color.red, base, PLAYER_NAME_Y);

        if (summaryEmojis == null || summaryEmojis.isEmpty()) return;

        int fullEmojiWidth = EMOJI_SIZE + EMOJI_MARGIN;
        Point summaryPoint = new Point(base.x + 172 - (EMOJI_SIZE / 2), base.y + PLAYER_NAME_Y + 5);
        if (summaryEmojis.size() % 2 == 0) {
            summaryPoint.translate(-fullEmojiWidth / 2, 0);
        }
        if (summaryEmojis.size() > 2) {
            summaryPoint.translate(-fullEmojiWidth * (summaryEmojis.size() - 1) / 2, 0);
        }
        for (TI4Emoji summaryEmoji : summaryEmojis) {
            BufferedImage summaryImage = RemoteImages.emoji(summaryEmoji, EMOJI_SIZE);
            graphics.setColor(Color.black);
            graphics.drawImage(summaryImage, summaryPoint.x, summaryPoint.y, null);
            summaryPoint.translate(fullEmojiWidth, 0);
        }
    }
}
