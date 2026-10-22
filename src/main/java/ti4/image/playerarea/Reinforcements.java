package ti4.image.playerarea;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.image.draw.PaImage;
import ti4.image.draw.PlayerTokens;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.UnitTokenPosition;
import ti4.service.map.ReinforcementStock;

final class Reinforcements {

    private static final String CC_TAG = "cc";
    private static final int SHEET_OFFSET = 440;
    private static final int BLOCK_WIDTH = 450;

    private Reinforcements() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        int x = slot.rightEdge() - SHEET_OFFSET - xDeltaFromRight;
        PaImage.draw(ctx.g2(), x, y, "pa_reinforcements.png");
        Map<UnitKey, Integer> unitsOnBoard = ctx.unitsOnBoard();
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
        ReinforcementStock stock = ReinforcementStock.of(game, player, unitID, unitsOnBoard);
        if (stock == null) {
            return;
        }
        UnitKey unitKey = stock.unitKey();
        UnitTokenPosition reinforcementsPosition = stock.position();
        int numInReinforcements = stock.remaining();
        boolean voltron =
                stock.model() != null && "naaz_voltron".equals(stock.model().getAlias());
        BufferedImage voltronDecal = ImageHelper.read(ImageResources.decal("Voltron.png"));

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
