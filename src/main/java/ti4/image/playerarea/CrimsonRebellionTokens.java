package ti4.image.playerarea;

import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class CrimsonRebellionTokens {

    private static final int BREACH_TOKENS = 7;
    private static final int SEVER_TOKENS = 1;

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
        int totalBreaches = countBreaches(ctx.game());
        if (totalBreaches > BREACH_TOKENS) {
            warnTooMany(ctx, player, "breach", totalBreaches, "Breach tokens");
        }
        return TokenSupply.drawRemaining(
                ctx,
                slot,
                TokenSupply.zigzag(BREACH_TOKENS, 35, 25),
                ImageHelper.read(breachFile),
                BREACH_TOKENS - totalBreaches,
                xDeltaFromRight,
                y);
    }

    private static int drawSeverTokens(
            PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        String severFile = ImageResources.token(Constants.TOKEN_SEVERED);
        int severTokens = (int) ctx.game().getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .filter(unitHolder -> unitHolder.getTokenList().contains(Constants.TOKEN_SEVERED))
                .count();
        if (severTokens > SEVER_TOKENS) {
            warnTooMany(ctx, player, "sever", severTokens, "Sever tokens");
        }
        return TokenSupply.drawRemaining(
                ctx,
                slot,
                TokenSupply.zigzag(SEVER_TOKENS, 20, 0),
                ImageHelper.read(severFile),
                SEVER_TOKENS - severTokens,
                xDeltaFromRight,
                y);
    }

    private static int countBreaches(Game game) {
        return (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .flatMap(unitHolder -> unitHolder.getTokenList().stream())
                .filter(token ->
                        Constants.TOKEN_BREACH_ACTIVE.equals(token) || Constants.TOKEN_BREACH_INACTIVE.equals(token))
                .count();
    }

    private static void warnTooMany(PlayerAreaContext ctx, Player player, String kind, int count, String tokens) {
        String message = player.getRepresentation() + ", there are too many " + tokens
                + " on the board. Please review and resolve manually.";
        ctx.warnings().warn(player, kind, count, message);
    }
}
