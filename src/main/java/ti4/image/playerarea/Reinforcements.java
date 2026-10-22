package ti4.image.playerarea;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.PaImage;
import ti4.image.draw.PlayerTokens;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.model.UnitTokenPosition;

final class Reinforcements {

    private static final String CC_TAG = "cc";
    private static final int SHEET_OFFSET = 440;
    private static final int BLOCK_WIDTH = 450;

    private Reinforcements() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        int x = slot.rightEdge() - SHEET_OFFSET - xDeltaFromRight;
        PaImage.draw(ctx.g2(), x, y, "pa_reinforcements.png");
        Map<UnitKey, Integer> unitsOnBoard = ctx.reinforcementCounts().unitsOnBoard(ctx.game());
        for (String unitID : Mapper.getUnitIDList()) {
            drawUnit(ctx, player, unitID, unitsOnBoard, x, y);
        }
        drawCommandTokens(ctx, player, x, y);
        return xDeltaFromRight + BLOCK_WIDTH;
    }

    private static void drawUnit(
            PlayerAreaContext ctx, Player player, String unitID, Map<UnitKey, Integer> unitsOnBoard, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        String playerColor = player.getColor();
        UnitKey unitKey = Mapper.getUnitKey(unitID, playerColor);
        if ("csd".equals(unitID)) return;
        if ("cff".equals(unitID)) unitKey = Mapper.getUnitKey("ff", playerColor);
        if ("cgf".equals(unitID)) unitKey = Mapper.getUnitKey("gf", playerColor);

        int count = unitsOnBoard.getOrDefault(unitKey, 0);
        if ((player.ownsUnit("cabal_spacedock")
                        || player.ownsUnit("cabal_spacedock2")
                        || player.hasTech("tf-dimensionaltear"))
                && "sd".equals(unitID)) {
            count += unitsOnBoard.getOrDefault(Mapper.getUnitKey("csd", playerColor), 0);
        }

        UnitTokenPosition reinforcementsPosition = MapPositionMapper.getReinforcementsPosition(unitID);
        if (reinforcementsPosition == null) {
            return;
        }
        int unitCap = player.getUnitCap(unitID);
        if (unitCap == 0 && !"fs".equals(unitID)) {
            unitCap = reinforcementsPosition.getPositionCount(unitID);
        }
        unitCap -= ("ws".equals(unitID) && player.ownsUnit("tf-dragonfreed")) ? 1 : 0;

        UnitModel model = player.getUnitFromUnitKey(unitKey);
        boolean voltron = model != null && "naaz_voltron".equals(model.getAlias());
        BufferedImage voltronDecal = ImageHelper.read(ImageResources.decal("Voltron.png"));

        int numInReinforcements = unitCap - count;
        if ((player.hasUnlockedBreakthrough("freesystemsbt") || player.hasTech("tf-rallyingmarshalls"))
                && model != null
                && model.getUnitType() == UnitType.Infantry) {
            numInReinforcements = withRallyingInfantry(game, player, numInReinforcements, unitCap);
        }
        BufferedImage image = ImageHelper.read(ImageResources.unit(unitKey));
        String decal = player.getDecalFile(unitID);
        BufferedImage decalImage = null;
        if (decal != null) {
            decalImage = ImageHelper.read(ImageResources.decal(decal));
        }
        for (int i = 0; i < numInReinforcements; i++) {
            Point position = reinforcementsPosition.getPosition(unitID);
            if (position == null) break;

            g2.drawImage(image, x + position.x, y + position.y, null);
            if (decalImage != null) {
                g2.drawImage(decalImage, x + position.x, y + position.y, null);
            }
            if (voltron && voltronDecal != null) {
                g2.drawImage(voltronDecal, x + position.x, y + position.y, null);
            }
        }

        if (!game.isHasEnded() && numInReinforcements < 0 && game.isCcNPlasticLimit()) {
            warnOverLimit(ctx, player, unitKey, numInReinforcements);
        }

        int offset = 0;
        if ("ws".equals(unitID) && player.ownsUnit("tf-dragonfreed")) {
            offset = 16;
            Point position =
                    MapPositionMapper.getReinforcementsPosition("number_ws").getPosition("number_ws");
            PaImage.draw(
                    g2,
                    x + position.x - offset,
                    y + position.y,
                    "pa_reinforcements_dragon"
                            + (numInReinforcements > 0
                                    ? Mapper.getColor(playerColor).blackWhiteFileSuffix()
                                    : "_red.png"));
        }

        if (numInReinforcements > -10) {
            ReinforcementNumber.paint(g2, unitID, x + offset, y, numInReinforcements, playerColor);
        }
    }

    private static int withRallyingInfantry(Game game, Player player, int numInReinforcements, int unitCap) {
        for (String planet : player.getPlanetsAllianceMode()) {
            UnitHolder planetUh = game.getUnitHolderFromPlanet(planet);
            if (planetUh != null && planetUh.getUnitCount(UnitType.Infantry, player) > 0) {
                numInReinforcements += 1;
                numInReinforcements = Math.min(numInReinforcements, unitCap);
            }
        }
        return numInReinforcements;
    }

    private static void warnOverLimit(PlayerAreaContext ctx, Player player, UnitKey unitKey, int inReinforcements) {
        Game game = ctx.game();
        String unitName = unitKey.unitType().humanReadableName();
        String warningMessage = player.getRepresentation()
                + " is exceeding unit plastic or cardboard limits for " + unitName
                + ". Use buttons to remove (note that you cannot remove plastic pieces from systems with your command token in them).";
        List<Button> removeButtons =
                ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, unitKey.asyncID());
        ctx.warnings()
                .warnWithButtons(
                        player, "plastic_" + unitKey.asyncID(), inReinforcements, warningMessage, removeButtons);
    }

    private static void drawCommandTokens(PlayerAreaContext ctx, Player player, int x, int y) {
        String playerColor = player.getColor();
        int ccCount = Helper.getCCCount(ctx.game(), playerColor);
        UnitTokenPosition reinforcementsPosition = MapPositionMapper.getReinforcementsPosition(CC_TAG);
        if (reinforcementsPosition == null || playerColor == null) {
            return;
        }
        int remainingReinforcements = player.getCommandTokenLimit() - ccCount;
        if (remainingReinforcements > 0) {
            for (int i = 0; i < remainingReinforcements && i < 16; i++) {
                try {
                    String ccID = Mapper.getCCID(playerColor);
                    Point position = reinforcementsPosition.getPosition(CC_TAG);
                    PlayerTokens.drawCommandCounters(
                            ctx.g2(), ccID, x + position.x, y + position.y, 1, player, false, true);
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(player), "Could not parse file for CC: " + playerColor, e);
                }
            }
        }
        if (remainingReinforcements >= -5) {
            ReinforcementNumber.paint(ctx.g2(), CC_TAG, x, y, remainingReinforcements, playerColor);
        }
    }
}
