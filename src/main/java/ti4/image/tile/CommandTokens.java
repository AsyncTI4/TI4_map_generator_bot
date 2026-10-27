package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.image.draw.PlayerTokens;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;

final class CommandTokens {

    private static final int LEFT_MARGIN = 10;
    private static final int LIFT = 40;

    private CommandTokens() {}

    static void draw(TileScene scene, Tile tile, Graphics2D g, UnitHolder spaceHolder) {
        int deltaX = 0;
        int deltaY = 0;
        for (String ccID : spaceHolder.getCcList()) {
            String ccPath = ImageResources.commandToken(ccID);
            if (ccPath == null) {
                continue;
            }
            BufferedImage image = null;
            try {
                image = ImageHelper.read(ccPath);
                Point centerPosition = TokenPlanetPlacement.holderCenter(spaceHolder, tile);
                Player player =
                        PlayerTokens.ownerOfToken(scene.game().getPlayers().values(), ccID);
                boolean convertToGeneric = scene.showsGeneric(player);
                boolean hiddenSweep = ccID.startsWith("sweep") && player != scene.fowPlayer();
                if (!hiddenSweep) {
                    int imgX = TILE_PADDING + LEFT_MARGIN + deltaX;
                    int imgY = TILE_PADDING + centerPosition.y - LIFT + deltaY;
                    PlayerTokens.drawCommandCounters(g, ccID, imgX, imgY, 1, player, convertToGeneric, true);
                }
            } catch (Exception e) {
                BotLogger.error("Could not addCC", e);
            }

            if (image != null) {
                deltaX += image.getWidth() / 5;
                deltaY += image.getHeight() / 4;
            }
        }
    }
}
