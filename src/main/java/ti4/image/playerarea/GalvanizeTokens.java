package ti4.image.playerarea;

import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.image.io.ImageHelper;

final class GalvanizeTokens {

    private static final int GALVANIZE_TOKENS = 7;

    private GalvanizeTokens() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (!player.hasAbility("galvanize")) {
            return xDeltaFromRight;
        }
        Game game = ctx.game();
        String tokenFile = ResourceHelper.getResourceFromFolder("extra/", "marker_galvanize.png");
        int galvanized = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .mapToInt(UnitHolder::getTotalGalvanizedCount)
                .sum();
        if (!game.isFrankenGame() && !hasNonBastionGalvanizeSource(game) && galvanized > GALVANIZE_TOKENS) {
            String message = player.getRepresentation()
                    + ", there are too many Galvanized units on the board. Please review and resolve manually.";
            ctx.warnings().warn(player, "galvanize", galvanized, message);
        }
        return TokenSupply.drawRemaining(
                ctx,
                slot,
                TokenSupply.zigzag(GALVANIZE_TOKENS, 20, 20),
                ImageHelper.read(tokenFile),
                GALVANIZE_TOKENS - galvanized,
                xDeltaFromRight,
                y);
    }

    private static boolean hasNonBastionGalvanizeSource(Game game) {
        return game.getRealPlayers().stream()
                .anyMatch(player -> player.hasTech("thardentiar")
                        || player.hasTech("thponthousr")
                        || player.ownsPromissoryNote("thpnponthous")
                        || player.hasUnlockedBreakthrough("kryxosbt"));
    }
}
