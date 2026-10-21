package ti4.image.tile;

import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class LawWormholeMarks {

    private static final int BLOCKED_OFFSET = 40;

    private LawWormholeMarks() {}

    static boolean isReconstructionInPlay(Game game) {
        return ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon");
    }

    static boolean isTravelBanInPlay(Game game) {
        return ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban");
    }

    static boolean isNexusLawInPlay(Game game) {
        return ButtonHelper.isLawInPlay(game, "nexus") || ButtonHelper.isLawInPlay(game, "absol_nexus");
    }

    static BufferedImage blockedWormholeImage(boolean half) {
        return ImageHelper.read(ImageResources.token("agenda_wormhole_blocked" + (half ? "_half" : "") + ".png"));
    }

    static BufferedImage reconstructionPartnerImage(String partnerWormhole) {
        return ImageHelper.readScaled(ImageResources.token("token_wh" + partnerWormhole + ".png"), 40.0f / 65);
    }

    static void draw(Game game, TileLayer layer) {
        Tile tile = layer.tile();
        boolean reconstruction = isReconstructionInPlay(game);
        if (isTravelBanInPlay(game)
                && (Mapper.getWormholes(tile.getTileID()).contains(Constants.ALPHA)
                        || Mapper.getWormholes(tile.getTileID()).contains(Constants.BETA))) {
            WormholeIconSpots.draw(tile, layer.g(), blockedWormholeImage(reconstruction), BLOCKED_OFFSET, "ab", false);
        } else if (tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_SEVERED)) {
            WormholeIconSpots.draw(tile, layer.g(), blockedWormholeImage(reconstruction), BLOCKED_OFFSET, "", true);
        }
        if (reconstruction && Mapper.getWormholes(tile.getTileID()).contains(Constants.ALPHA)) {
            WormholeIconSpots.draw(tile, layer.g(), reconstructionPartnerImage("beta"), 0, "a", false);
        }
        if (reconstruction && Mapper.getWormholes(tile.getTileID()).contains(Constants.BETA)) {
            WormholeIconSpots.draw(tile, layer.g(), reconstructionPartnerImage("alpha"), 0, "b", false);
        }
        if (isNexusLawInPlay(game) && "82b".equals(tile.getTileID()) && !isTravelBanInPlay(game)) {
            WormholeIconSpots.draw(tile, layer.g(), blockedWormholeImage(reconstruction), BLOCKED_OFFSET, "ab", false);
        }
        if (ButtonHelper.isLawInPlay(game, "shared_research") && tile.isNebula(game)) {
            drawNebulaBypass(layer);
        }
    }

    private static void drawNebulaBypass(TileLayer layer) {
        Tile tile = layer.tile();
        BufferedImage nebulaBypass = ImageHelper.read(ImageResources.token("agenda_shared_research.png"));
        if (layer.isSpiral()) {
            switch (tile.getTileID()) {
                case "51" -> layer.drawOnHex(nebulaBypass, 42, 235);
                case "82b" -> layer.drawOnHex(nebulaBypass, 30, 221);
                case "82a" -> layer.drawOnHex(nebulaBypass, 63, 273);
                default -> layer.drawOnHex(nebulaBypass, 99, 294);
            }
        } else if (tile.isHomeSystem()) {
            layer.drawOnHex(nebulaBypass, 42, 193);
        } else {
            layer.drawOnHex(nebulaBypass, 80, 236);
        }
    }
}
