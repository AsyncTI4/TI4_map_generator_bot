package ti4.image.playerarea;

import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.OutlinedText;
import ti4.service.game.MonumentsService;

final class WinnuVault {

    private WinnuVault() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        Game game = ctx.game();
        if (!game.isMonumentsMode()
                || (!player.hasUnit("winnu_monument")
                        && !MonumentsService.isMonumentOnBoard(game, player, "winnu_monument"))) {
            return xDeltaFromRight;
        }
        OutlinedText.drawCenteredWhiteOnBlack(
                ctx.g2(),
                "Vault Trade Goods: " + MonumentsService.getWinnuMonumentTradeGoodCount(game, player),
                slot.rightEdge() - xDeltaFromRight - 300,
                y + 125);
        return xDeltaFromRight + 300;
    }
}
