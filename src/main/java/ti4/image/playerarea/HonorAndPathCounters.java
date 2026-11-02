package ti4.image.playerarea;

import java.awt.Graphics2D;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsTEButtonHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.TwilightsFallMonumentsButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.OutlinedText;
import ti4.service.VeiledHeartService;
import ti4.service.game.MonumentsService;

final class HonorAndPathCounters {

    private static final int BLOCK_WIDTH = 200;
    private static final int TEXT_OFFSET_FROM_DELTA = 300;

    private HonorAndPathCounters() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        Game game = ctx.game();
        boolean hasAuraVault = game.isMonumentsMode() && player.hasUnit("yellowtf_monument");
        boolean hasPharusIustitiae = game.isMonumentsMode()
                && (player.hasUnit("keleres_monument")
                        || MonumentsService.isMonumentOnBoard(game, player, "keleres_monument"));
        boolean hasCounters = player.getDishonorCounter() > 0
                || player.getHonorCounter() > 0
                || player.getPathTokenCounter() > 0
                || player.getSteelbalanceCounter() > 0
                || player.getStarbalanceCounter() > 0
                || game.isVeiledHeartMode();
        if (!hasCounters && !hasAuraVault && !hasPharusIustitiae) {
            return xDeltaFromRight;
        }
        Graphics2D g2 = ctx.g2();
        int x = slot.rightEdge() - xDeltaFromRight - TEXT_OFFSET_FROM_DELTA;
        if (!hasCounters) {
            int tokenOffset = 50;
            if (hasAuraVault) {
                OutlinedText.drawCenteredWhiteOnBlack(g2, auraTokens(game, player), x, y + tokenOffset);
                tokenOffset += 50;
            }
            if (hasPharusIustitiae) {
                OutlinedText.drawCenteredWhiteOnBlack(g2, pharusTokens(game, player), x, y + tokenOffset);
            }
            return xDeltaFromRight + BLOCK_WIDTH;
        }
        if (game.isVeiledHeartMode()) {
            OutlinedText.drawCenteredWhiteOnBlack(
                    g2, "Veiled Cards: " + VeiledHeartService.countVeiledCards(player), x, y + 50);
        } else {
            drawCounters(g2, player, x, y);
        }
        if (hasAuraVault) {
            OutlinedText.drawCenteredWhiteOnBlack(g2, auraTokens(game, player), x, y + 150);
        }
        if (hasPharusIustitiae) {
            OutlinedText.drawCenteredWhiteOnBlack(g2, pharusTokens(game, player), x, y + (hasAuraVault ? 200 : 150));
        }
        return xDeltaFromRight + BLOCK_WIDTH;
    }

    private static void drawCounters(Graphics2D g2, Player player, int x, int y) {
        if (player.getHonorCounter() > 0 || player.getDishonorCounter() > 0) {
            OutlinedText.drawCenteredWhiteOnBlack(g2, "Honor Count: " + player.getHonorCounter(), x, y + 50);
        } else if (player.getSteelbalanceCounter() > 0 || player.getStarbalanceCounter() > 0) {
            OutlinedText.drawCenteredWhiteOnBlack(g2, "Steel Count: " + player.getSteelbalanceCounter(), x, y + 50);
            OutlinedText.drawCenteredWhiteOnBlack(g2, "Star Count: " + player.getStarbalanceCounter(), x, y + 100);
        } else {
            OutlinedText.drawCenteredWhiteOnBlack(g2, "Path Token Count: " + player.getPathTokenCounter(), x, y + 50);
        }
        if (player.getDishonorCounter() > 0) {
            OutlinedText.drawCenteredWhiteOnBlack(g2, "Dishonor Count: " + player.getDishonorCounter(), x, y + 100);
        }
    }

    private static String auraTokens(Game game, Player player) {
        return "Aura Tokens: " + TwilightsFallMonumentsButtonHandler.getYellowTfMonumentCommandTokenCount(game, player);
    }

    private static String pharusTokens(Game game, Player player) {
        return "Pharus Tokens: " + MonumentsTEButtonHandler.getKeleresMonumentCommandTokenCount(game, player);
    }
}
