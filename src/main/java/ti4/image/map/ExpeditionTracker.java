package ti4.image.map;

import java.awt.Graphics2D;
import java.util.List;
import ti4.game.Expeditions;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.PaImage;
import ti4.image.draw.PlayerTokens;

final class ExpeditionTracker {

    private record Claim(String claimant, int offsetX, int offsetY) {}

    private ExpeditionTracker() {}

    static boolean isShown(Game game) {
        return game.isThundersEdge()
                && game.getExpeditions().getRemainingExpeditionCount() > 0
                && game.getTileFromPlanet("thundersedge") == null
                && !game.isTwilightsFallMode();
    }

    static void draw(RenderContext ctx, Graphics2D g2, int x, int y) {
        Game game = ctx.game();
        if (!isShown(game)) return;
        Expeditions exp = game.getExpeditions();

        PaImage.drawGeneral(g2, x, y, "Expeditions.png");
        List<Claim> claims = List.of(
                new Claim(exp.getTradeGoods(), 47, 101),
                new Claim(exp.getFiveRes(), 114, 5),
                new Claim(exp.getActionCards(), 182, 101),
                new Claim(exp.getTechSkip(), 47, 150),
                new Claim(exp.getSecret(), 114, 243),
                new Claim(exp.getFiveInf(), 182, 150));
        for (Claim claim : claims) {
            if (claim.claimant() != null) {
                Player p = game.getPlayerFromColorOrFaction(claim.claimant());
                PlayerTokens.readAndDrawControlToken(
                        g2,
                        p,
                        x + claim.offsetX(),
                        y + claim.offsetY(),
                        ctx.fog().isPrivate(),
                        1.0f);
            }
        }
    }
}
