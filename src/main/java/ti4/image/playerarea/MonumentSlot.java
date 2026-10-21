package ti4.image.playerarea;

import java.awt.image.BufferedImage;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.FactionIcons;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.game.MonumentsService;
import ti4.service.game.NekroMonumentService;

final class MonumentSlot {

    private static final int BLOCK_WIDTH = 120;

    private MonumentSlot() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        Game game = ctx.game();
        UnitModel monument = player.getUnitByBaseType("monument");
        if (!game.isMonumentsMode() || monument == null) {
            return xDeltaFromRight;
        }

        int x = slot.rightEdge() - BLOCK_WIDTH - xDeltaFromRight;
        BufferedImage image;
        if (MonumentsService.hasMonumentOnBoard(game, player)) {
            image = ImageHelper.read(ResourceHelper.getResourceFromFolder("units/", "outline_monument.png"));
        } else {
            image = ImageHelper.read(
                    ImageResources.unit(Mapper.getUnitKey(monument.getUnitType().toString(), player.getColor())));
        }
        ctx.g2().drawImage(image, x + 20, y + 40, null);
        if (NekroMonumentService.hasAssimilatorOnMonument(game, player)) {
            FactionIcons.drawFactionBordered(ctx.g2(), "nekro", x + 52, y + 78, 36, 36, true);
        }
        return xDeltaFromRight + BLOCK_WIDTH;
    }
}
