package ti4.image.playerarea;

import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.service.map.PlayerAreaSupplies;

final class CrimsonRebellionTokens {

    private CrimsonRebellionTokens() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (player.hasAbility("incursion")) {
            xDeltaFromRight = drawBreachTokens(ctx, slot, player, xDeltaFromRight, y);
        }
        if (player.ownsPromissoryNote("sever")) {
            xDeltaFromRight = drawSeverTokens(ctx, slot, player, xDeltaFromRight, y);
        }
        return xDeltaFromRight;
    }

    private static int drawBreachTokens(
            PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        String breachFile = ImageResources.token(Constants.TOKEN_BREACH_ACTIVE);
        int totalBreaches = PlayerAreaSupplies.breachCount(ctx.game());
        return TokenSupply.drawRemaining(
                ctx,
                slot,
                TokenSupply.zigzag(PlayerAreaSupplies.BREACH_TOKENS, 35, 25),
                ImageHelper.read(breachFile),
                PlayerAreaSupplies.BREACH_TOKENS - totalBreaches,
                xDeltaFromRight,
                y);
    }

    private static int drawSeverTokens(
            PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        String severFile = ImageResources.token(Constants.TOKEN_SEVERED);
        int severTokens = PlayerAreaSupplies.severCount(ctx.game());
        return TokenSupply.drawRemaining(
                ctx,
                slot,
                TokenSupply.zigzag(PlayerAreaSupplies.SEVER_TOKENS, 20, 0),
                ImageHelper.read(severFile),
                PlayerAreaSupplies.SEVER_TOKENS - severTokens,
                xDeltaFromRight,
                y);
    }
}
