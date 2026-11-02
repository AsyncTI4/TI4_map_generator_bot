package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.tile.UnitPlacement.ImagePosition;
import ti4.model.Mapper;
import ti4.model.UnitModel;

final class UnitBadges {

    private static final Point COUNT_POSITION = new Point(40, 27);
    private static final int SPACE_DAMAGE_SHIFT_X = 7;

    private UnitBadges() {}

    static void drawTag(
            Graphics2D g, Game game, UnitKey unitKey, Player player, ImagePosition imagePos, int iteration) {
        if (iteration != 0 || unitKey.unitType() == UnitType.Infantry || !game.isShowUnitTags()) {
            return;
        }
        UnitModel unitModel = game.getUnitFromUnitKey(unitKey);
        if (player == null || unitModel == null || !unitModel.getIsShip()) {
            return;
        }

        String factionTag = player.getFactionModel().getShortTag();
        BufferedImage plaquette = ImageHelper.read(ImageResources.unit("unittags_plaquette.png"));
        Point plaquetteOffset = tagOffset(unitKey.asyncID());
        int tagX = imagePos.x() + plaquetteOffset.x;
        int tagY = imagePos.y() + plaquetteOffset.y;

        g.drawImage(plaquette, tagX, tagY, null);
        FactionIcons.draw(g, player, tagX, tagY, 32, 32);
        g.setColor(Color.WHITE);
        OutlinedText.drawCentered(g, factionTag, new Rectangle(tagX + 25, tagY + 17, 40, 13), Fonts.ti(13));
    }

    private static Point tagOffset(String unitID) {
        return switch (unitID) {
            case "ws" -> new Point(-10, 45);
            case "fs", "lord", "lady", "tyrantslament", "cavalry", "celagrom", "aurelion" -> new Point(10, 55);
            case "dn" -> new Point(10, 50);
            case "ca", "cv" -> new Point(0, 40);
            case "gf", "ff" -> new Point(-15, 12);
            case "dd" -> new Point(-10, 30);
            case "mf", "pd", "sd", "csd", "plenaryorbital" -> new Point(-10, 20);
            default -> new Point(0, 0);
        };
    }

    static void drawBulkCount(Graphics2D g, UnitKey unitKey, int count, ImagePosition imagePos) {
        boolean blackText =
                switch (Mapper.getColor(unitKey.colorID()).getTextColor()) {
                    case "black" -> true;
                    default -> false;
                };
        Color color = blackText ? Color.BLACK : Color.WHITE;
        Color outline = blackText ? Color.WHITE : Color.BLACK;
        g.setFont(count > 9 ? Fonts.ti(28) : Fonts.ti(24));
        int offsetX = COUNT_POSITION.x + (count > 9 ? 5 : 0);
        int offsetY = COUNT_POSITION.y + (count > 9 ? 5 : 0);
        OutlinedText.draw(
                g,
                count == 1 ? " 1" : Integer.toString(count),
                imagePos.x() + offsetX,
                imagePos.y() + offsetY,
                color,
                HorizontalAlign.Left,
                VerticalAlign.Bottom,
                new BasicStroke(4.0f),
                outline);
    }

    static void drawGalvanizeTag(
            Graphics2D g,
            Tile tile,
            UnitHolder unitHolder,
            Point unitPos,
            ImagePosition imagePos,
            BufferedImage unitImage,
            BufferedImage galvTag,
            UnitType unitType,
            int amount) {
        Point adjustedUnitPos = TokenPlanets.offset(unitPos, unitHolder, tile);
        int imageDmgX = centeredX(adjustedUnitPos, imagePos, unitImage, galvTag);
        int imageDmgY = centeredY(adjustedUnitPos, imagePos, unitImage, galvTag);
        if (unitType == UnitType.Infantry || unitType == UnitType.Fighter) {
            imageDmgX += 33;
            imageDmgY -= 15;
        } else {
            imageDmgX += 5;
            imageDmgY -= 20;
        }

        imageDmgX += TILE_PADDING;
        imageDmgY += TILE_PADDING;
        g.drawImage(galvTag, imageDmgX, imageDmgY, null);
        g.setFont(Fonts.ti(20));
        OutlinedText.drawCentered(
                g, Integer.toString(amount), imageDmgX + 38, imageDmgY + 16, Color.white, null, Color.black);
    }

    static void drawDamage(
            Graphics2D g,
            UnitPlacement placement,
            Point unitPos,
            ImagePosition imagePos,
            BufferedImage unitImage,
            BufferedImage dmgImage,
            UnitType unitType) {
        int imageDmgX;
        int imageDmgY;
        if (placement.isTokenPlanet()) {
            imageDmgX = imagePos.x() - TILE_PADDING;
            imageDmgY = imagePos.y() - TILE_PADDING;
        } else if (unitType == UnitType.Mech) {
            imageDmgX = unitPos != null ? unitPos.x : imagePos.originalX() - dmgImage.getWidth();
            imageDmgY = unitPos != null ? unitPos.y : imagePos.originalY() - dmgImage.getHeight();
        } else {
            imageDmgX = centeredX(unitPos, imagePos, unitImage, dmgImage);
            imageDmgY = centeredY(unitPos, imagePos, unitImage, dmgImage);
        }
        if (unitPos != null && placement.isSpace()) {
            imageDmgX -= SPACE_DAMAGE_SHIFT_X;
        }
        g.drawImage(dmgImage, TILE_PADDING + imageDmgX, TILE_PADDING + imageDmgY, null);
    }

    private static int centeredX(
            Point position, ImagePosition imagePosition, BufferedImage unitImage, BufferedImage badge) {
        return position != null
                ? position.x + (unitImage.getWidth() / 2) - (badge.getWidth() / 2)
                : imagePosition.originalX() - (badge.getWidth() / 2);
    }

    private static int centeredY(
            Point position, ImagePosition imagePosition, BufferedImage unitImage, BufferedImage badge) {
        return position != null
                ? position.y + (unitImage.getHeight() / 2) - (badge.getHeight() / 2)
                : imagePosition.originalY() - (badge.getHeight() / 2);
    }
}
