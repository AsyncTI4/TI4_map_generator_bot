package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.CalendarHelper;
import ti4.helpers.Constants;
import ti4.helpers.RenderRandom;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.UnitModel;

final class Nombox {

    private static final int BLOCK_WIDTH = 450;
    private static final Map<UnitType, Point> UNIT_OFFSETS = Map.ofEntries(
            Map.entry(UnitType.Infantry, new Point(50, 75)),
            Map.entry(UnitType.Fighter, new Point(50, 125)),
            Map.entry(UnitType.Mech, new Point(100, 63)),
            Map.entry(UnitType.Destroyer, new Point(144, 63)),
            Map.entry(UnitType.Cruiser, new Point(185, 55)),
            Map.entry(UnitType.Carrier, new Point(235, 58)),
            Map.entry(UnitType.Dreadnought, new Point(284, 54)),
            Map.entry(UnitType.Flagship, new Point(335, 47)),
            Map.entry(UnitType.Warsun, new Point(393, 56)),
            Map.entry(UnitType.Pds, new Point(340, 112)),
            Map.entry(UnitType.Spacedock, new Point(395, 112)),
            Map.entry(UnitType.Monument, new Point(235, 120)));
    private static final List<UnitType> DRAW_ORDER = List.of(
            UnitType.Mech,
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun,
            UnitType.Fighter,
            UnitType.Infantry);
    private static final List<UnitType> MONUMENTS_MODE_DRAW_ORDER =
            List.of(UnitType.Pds, UnitType.Spacedock, UnitType.Monument);

    private Nombox() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        int x = slot.rightEdge() - BLOCK_WIDTH - xDeltaFromRight;
        UnitHolder unitHolder = player.getNomboxTile().getSpaceUnitHolder();
        if (unitHolder == null || unitHolder.getUnitKeys().isEmpty()) {
            return xDeltaFromRight;
        }

        if (player.getFaction() != null) {
            BufferedImage factionIcon = FactionIcons.of(player);
            if (factionIcon != null) {
                g2.drawImage(factionIcon, x + 178, y + 33, null);
            }
        }
        PaImage.draw(g2, x, y, "pa_nombox.png");

        Map<UnitType, List<UnitKey>> keysByType =
                capturedUnitKeys(unitHolder).stream().collect(Collectors.groupingBy(UnitKey::unitType));
        List<UnitType> order = new ArrayList<>(DRAW_ORDER);
        if (game.isMonumentsMode()) {
            order.addAll(MONUMENTS_MODE_DRAW_ORDER);
        }
        BufferedImage image = null;
        for (UnitType unitType : order) {
            List<UnitKey> keys = keysByType.get(unitType);
            if (keys != null) {
                image = drawUnitType(ctx, player, unitHolder, keys, image, x, y);
            }
        }
        return xDeltaFromRight + BLOCK_WIDTH;
    }

    private static Set<UnitKey> capturedUnitKeys(UnitHolder unitHolder) {
        Set<UnitKey> tempUnits = unitHolder.getUnitKeys();
        Set<UnitKey> units = new HashSet<>();
        for (UnitKey id : tempUnits) {
            if (id.unitType() == UnitType.Mech) {
                units.add(id);
            }
        }
        for (UnitKey key : units) {
            tempUnits.remove(key);
        }
        units.addAll(tempUnits);
        return units;
    }

    private static BufferedImage drawUnitType(
            PlayerAreaContext ctx,
            Player player,
            UnitHolder unitHolder,
            List<UnitKey> keys,
            BufferedImage image,
            int x,
            int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        int countOfUnits = 0;
        for (UnitKey key : keys) {
            countOfUnits += unitHolder.getUnitCount(key);
        }
        int deltaY = 0;
        for (UnitKey unitKey : keys) {
            int unitCount = unitHolder.getUnitCount(unitKey);
            Integer bulkUnitCount = null;
            Player owner = game.getPlayerFromColorOrFaction(unitKey.getColor());

            try {
                String unitPath = ImageResources.unit(unitKey);
                if (unitPath != null) {
                    if (unitKey.unitType() == UnitType.Fighter) {
                        unitPath = unitPath.replace(Constants.COLOR_FF, Constants.BULK_FF);
                        bulkUnitCount = unitCount;
                    } else if (unitKey.unitType() == UnitType.Infantry) {
                        unitPath = unitPath.replace(Constants.COLOR_GF, Constants.BULK_GF);
                        bulkUnitCount = unitCount;
                    }
                }
                image = ImageHelper.read(unitPath);
            } catch (Exception e) {
                BotLogger.error(
                        new LogOrigin(player),
                        "Could not parse unit file for: " + unitKey + " in game " + game.getName(),
                        e);
            }
            if (bulkUnitCount != null && bulkUnitCount > 0) {
                unitCount = 1;
            }
            if (image == null) {
                BotLogger.error(new LogOrigin(player), "Could not find unit image for: " + unitKey);
                continue;
            }

            Point position = new Point(x, y);
            Point offset = UNIT_OFFSETS.get(unitKey.unitType());
            if (offset != null) {
                position.translate(offset.x, offset.y);
            }
            boolean justNumber = unitKey.unitType() == UnitType.Fighter || unitKey.unitType() == UnitType.Infantry;
            UnitModel model = owner == null ? null : owner.getUnitFromUnitKey(unitKey);
            boolean voltron = model != null && "naaz_voltron".equals(model.getAlias());
            BufferedImage voltronDecal = ImageHelper.read(ImageResources.decal("Voltron.png"));
            BufferedImage spoopy = unitKey.unitType() == UnitType.Warsun ? rollSpoopyWarsun() : null;

            if (justNumber) {
                g2.setFont(Fonts.ti(40));
                g2.setColor(Color.WHITE);
                g2.drawString(Integer.toString(countOfUnits), position.x, position.y);
                break;
            }
            position.y -= (countOfUnits * 7);

            Optional<BufferedImage> decal = Optional.ofNullable(owner)
                    .map(p -> p.getDecalFile(unitKey.asyncID()))
                    .map(decalFileName -> ImageHelper.read(ImageResources.decal(decalFileName)));

            for (int i = 0; i < unitCount; i++) {
                g2.drawImage(image, position.x, position.y + deltaY, null);
                if (decal.isPresent()) {
                    g2.drawImage(decal.get(), position.x, position.y + deltaY, null);
                }
                if (spoopy != null) {
                    g2.drawImage(spoopy, position.x, position.y + deltaY, null);
                }
                if (voltron) {
                    g2.drawImage(voltronDecal, position.x, position.y + deltaY, null);
                }
                deltaY += 14;
            }
        }
        return image;
    }

    private static BufferedImage rollSpoopyWarsun() {
        int chanceToSeeSpoop = CalendarHelper.isNearHalloween() ? 10 : 1000;
        if (RenderRandom.oneIn("playerArea.spoopyWarsun", chanceToSeeSpoop)) {
            return ImageHelper.read(ImageResources.spoopyFace());
        }
        return null;
    }
}
