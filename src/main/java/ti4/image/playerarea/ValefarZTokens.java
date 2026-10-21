package ti4.image.playerarea;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.io.ImageHelper;

final class ValefarZTokens {

    private static final int MONUMENT_TOKENS = 7;

    private ValefarZTokens() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        Game game = ctx.game();
        boolean hasMonument = game.isMonumentsMode() && player.hasUnit("nekro_monument");
        if (!player.hasReadyBreakthrough("nekrobt") && !hasMonument) {
            return xDeltaFromRight;
        }
        BufferedImage image = ImageHelper.read(ResourceHelper.getResourceFromFolder("extra/", "marker_valefarZ.png"));
        int tokensUsed = (int) Math.min(
                MONUMENT_TOKENS,
                nonEmptyFactions(game.getStoredValue("valefarZ"))
                        + nonEmptyFactions(game.getStoredValue("nekroMonumentAssimilatorZ")));
        int maxTokens = hasMonument ? MONUMENT_TOKENS : game.getRealPlayers().size() - 1;
        int tokenCount = Math.max(0, maxTokens - tokensUsed);
        return TokenSupply.drawRemaining(
                ctx, slot, TokenSupply.zigzag(tokenCount, 35, 25), image, tokenCount, xDeltaFromRight, y);
    }

    private static long nonEmptyFactions(String storedFactions) {
        return Arrays.stream(storedFactions.split("\\|"))
                .filter(faction -> !faction.isEmpty())
                .count();
    }
}
