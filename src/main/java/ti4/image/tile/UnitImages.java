package ti4.image.tile;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import ti4.game.Player;
import ti4.helpers.CalendarHelper;
import ti4.helpers.RenderRandom;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.OutlinedText;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class UnitImages {

    private static final int SPOOKY_ODDS_NEAR_HALLOWEEN = 10;
    private static final int SPOOKY_ODDS = 1000;

    private UnitImages() {}

    @Nullable
    static BufferedImage factionDecal(Player player, UnitKey unitKey) {
        if (player == null) return null;
        String decalFileName = player.getDecalFile(unitKey.asyncID());
        if (isBlank(decalFileName)) {
            return null;
        }
        return ImageHelper.read(ImageResources.decal(decalFileName));
    }

    @Nullable
    static BufferedImage overlay(UnitKey unitKey, Player player) {
        BufferedImage overlay = null;
        int spookyOdds = CalendarHelper.isNearHalloween() ? SPOOKY_ODDS_NEAR_HALLOWEEN : SPOOKY_ODDS;
        if (unitKey.unitType() == UnitType.Warsun && RenderRandom.oneIn("unit.spoopyWarsun", spookyOdds)) {
            overlay = ImageHelper.read(ImageResources.spoopyFace());
        }
        if (unitKey.unitType() == UnitType.Lady) {
            overlay = decal("units_ds_ghemina_lady_wht.png");
        }
        if (unitKey.unitType() == UnitType.Celagrom) {
            overlay = decal("units_ds_ghemina_celagrom_wht.png");
        }
        if (unitKey.unitType() == UnitType.Aurelion) {
            overlay = aurelionLabel();
        }
        if (unitKey.unitType() == UnitType.Flagship && player.ownsUnit("ghemina_flagship_lord")) {
            overlay = decal("units_ds_ghemina_lord_wht.png");
        }
        return overlay;
    }

    private static BufferedImage decal(String name) {
        return ImageHelper.read(ImageResources.decal(name));
    }

    private static BufferedImage aurelionLabel() {
        BufferedImage label = new BufferedImage(81, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = label.createGraphics();
        graphics.rotate(Math.PI / 4, 40, 40);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        OutlinedText.draw(graphics, "Aurelion", -1, 45, Color.WHITE, null, null, new BasicStroke(1), Color.BLACK);
        graphics.dispose();
        return label;
    }
}
