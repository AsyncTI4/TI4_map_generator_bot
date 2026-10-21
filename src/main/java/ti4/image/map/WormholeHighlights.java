package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.tile.TileGeometry;
import ti4.model.Mapper;

final class WormholeHighlights implements OffBoardHighlights {

    @Override
    public int draw(Graphics2D g2, Game game, Player player, Point miscTile) {
        if (!"ghost".equalsIgnoreCase(player.getFaction())) {
            return 0;
        }
        int offBoardHighlighting = 0;
        boolean alphaOnMap = false;
        boolean betaOnMap = false;
        boolean gammaOnMap = false;
        String alphaID = Mapper.getTokenID("creussalpha");
        String betaID = Mapper.getTokenID("creussbeta");
        String gammaID = Mapper.getTokenID("creussgamma");
        for (Tile tile2 : game.getTileMap().values()) {
            Set<String> tileTokens = tile2.getUnitHolders().get("space").getTokenList();
            alphaOnMap |= tileTokens.contains(alphaID);
            betaOnMap |= tileTokens.contains(betaID);
            gammaOnMap |= tileTokens.contains(gammaID);
        }

        offBoardHighlighting = (alphaOnMap ? 0 : 1) + (betaOnMap ? 0 : 1) + (gammaOnMap ? 0 : 1);
        int x = miscTile.x + (TileGeometry.TILE_WIDTH - 80) / 2;
        x += (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 30 : 0);
        int y = miscTile.y + (TileGeometry.TILE_HEIGHT - 80) / 2 + (player.isSpeaker() ? 30 : 0);
        boolean reconstruction =
                (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
        boolean travelBan =
                ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban");

        if (!gammaOnMap) {
            String tokenFile = ImageResources.tokenOrAttachment(gammaID);
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            g2.drawImage(bufferedImage, x, y, null);
            x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
        }
        if (!betaOnMap) {
            String tokenFile = ImageResources.tokenOrAttachment(betaID);
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            g2.drawImage(bufferedImage, x, y, null);
            if (travelBan) {
                BufferedImage blockedWormholeImage = ImageHelper.read(
                        ImageResources.token("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                g2.drawImage(blockedWormholeImage, x + 40, y + 40, null);
            }
            if (reconstruction) {
                BufferedImage doubleWormholeImage =
                        ImageHelper.readScaled(ImageResources.token("token_whalpha.png"), 40.0f / 65);
                g2.drawImage(doubleWormholeImage, x, y, null);
            }
            x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
        }
        if (!alphaOnMap) {
            String tokenFile = ImageResources.tokenOrAttachment(alphaID);
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            g2.drawImage(bufferedImage, x, y, null);
            if (travelBan) {
                BufferedImage blockedWormholeImage = ImageHelper.read(
                        ImageResources.token("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                g2.drawImage(blockedWormholeImage, x + 40, y + 40, null);
            }
            if (reconstruction) {
                BufferedImage doubleWormholeImage =
                        ImageHelper.readScaled(ImageResources.token("token_whbeta.png"), 40.0f / 65);
                g2.drawImage(doubleWormholeImage, x, y, null);
            }
            x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
        }
        return offBoardHighlighting;
    }
}
