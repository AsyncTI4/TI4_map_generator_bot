package ti4.image.playerarea;

import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.io.ImageHelper;
import ti4.service.map.PlayerAreaSupplies;

final class GalvanizeTokens {

    private GalvanizeTokens() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (!player.hasAbility("galvanize")) {
            return xDeltaFromRight;
        }
        Game game = ctx.game();
        String tokenFile = ResourceHelper.getResourceFromFolder("extra/", "marker_galvanize.png");
        int galvanized = PlayerAreaSupplies.galvanizedCount(game);
        return TokenSupply.drawRemaining(
                ctx,
                slot,
                TokenSupply.zigzag(PlayerAreaSupplies.GALVANIZE_TOKENS, 20, 20),
                ImageHelper.read(tokenFile),
                PlayerAreaSupplies.GALVANIZE_TOKENS - galvanized,
                xDeltaFromRight,
                y);
    }
}
