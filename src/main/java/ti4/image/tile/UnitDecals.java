package ti4.image.tile;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.ResourceHelper;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronAbilitiesHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.VoltronDecal;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;
import ti4.model.UnitModel;

final class UnitDecals {

    private static final int BAD_POSITION_OVERHANG = 5;

    private UnitDecals() {}

    static void drawDimensionalTear(
            Graphics2D g, UnitKey unitKey, Player player, BufferedImage unitImage, int imageX, int imageY) {
        if (unitKey.unitType() != UnitType.Spacedock
                || !(player.ownsUnitSubstring("cabal_spacedock") || player.hasTech("tf-dimensionaltear"))) {
            return;
        }
        BufferedImage dimTear = ImageHelper.read(ImageResources.decal("DimensionalTear.png"));
        if (dimTear != null) {
            int dtX = imageX + (unitImage.getWidth() - dimTear.getWidth()) / 2;
            int dtY = imageY + (unitImage.getHeight() - dimTear.getHeight()) / 2;
            g.drawImage(dimTear, dtX, dtY, null);
        }
    }

    static boolean isMisplaced(
            boolean holderHasDmz, boolean isSpace, UnitModel unitModel, Player player, UnitKey unitKey) {
        boolean wrongPlace = holderHasDmz;
        wrongPlace |= isSpace
                && unitModel.getIsPlanetOnly()
                && !IronAbilitiesHandler.isExoAtmosphericMech(player, unitKey.unitType());
        wrongPlace |= !isSpace
                && unitModel.getIsSpaceOnly()
                && !IronAbilitiesHandler.isExoAtmosphericMech(player, unitKey.unitType());
        wrongPlace &= !unitModel.getIsStructure() || !player.hasAbility("miniaturization");
        return wrongPlace;
    }

    static void drawMisplacedMarker(Graphics2D g, UnitKey unitKey, boolean bulk, float scale, int imageX, int imageY) {
        String badPath = ResourceHelper.getPositionFile("badpos_" + (bulk ? "tkn_" : "") + unitKey.asyncID() + ".png");
        BufferedImage badPositionImage =
                scale == 1.0f ? ImageHelper.read(badPath) : ImageHelper.readScaled(badPath, scale);
        g.drawImage(badPositionImage, imageX - BAD_POSITION_OVERHANG, imageY - BAD_POSITION_OVERHANG, null);
    }

    static void drawOverlay(
            Graphics2D g, Tile tile, UnitKey unitKey, Player player, BufferedImage overlay, int imageX, int imageY) {
        if ("81".equals(tile.getTileID())
                && "muaat".equals(player.getFaction())
                && unitKey.unitType() == UnitType.Warsun) {
            BufferedImage faceNovaSeed = ImageHelper.read(ImageResources.decal("NovaSeed.png"));
            g.drawImage(faceNovaSeed, imageX, imageY, null);
        } else if (overlay != null) {
            g.drawImage(overlay, imageX, imageY, null);
        }
    }

    static void drawLawDecals(Graphics2D g, Game game, UnitKey unitKey, int imageX, int imageY) {
        if (unitKey.unitType() == UnitType.Mech
                && ButtonHelper.anyLawInPlay(game, "articles_war", "absol_articleswar")) {
            drawToken(
                    g,
                    "agenda_articles_of_war"
                            + Mapper.getColor(unitKey.colorID()).blackWhiteFileSuffix(),
                    imageX,
                    imageY);
        }
        VoltronDecal.drawIfVoltron(unitKey, imageX, imageY, g, game);
        if (unitKey.unitType() == UnitType.Warsun && ButtonHelper.isLawInPlay(game, "schematics")) {
            drawToken(
                    g,
                    "agenda_publicize_weapon_schematics"
                            + Mapper.getColor(unitKey.colorID()).blackWhiteFileSuffix(),
                    imageX,
                    imageY);
        }
    }

    private static void drawToken(Graphics2D g, String tokenFile, int imageX, int imageY) {
        g.drawImage(ImageHelper.read(ImageResources.token(tokenFile)), imageX, imageY, null);
    }
}
