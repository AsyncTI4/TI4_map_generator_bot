package ti4.image.playerarea;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class CreussWormholeTokens {

    private static final int TOKEN_STEP = 40;

    private CreussWormholeTokens() {}

    private record Wormholes(boolean alpha, boolean beta, boolean gamma) {
        boolean allOnMap() {
            return alpha && beta && gamma;
        }
    }

    private record LawOverlays(boolean travelBan, boolean reconstruction) {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (!"ghost".equalsIgnoreCase(player.getFaction())) {
            return xDeltaFromRight;
        }
        Game game = ctx.game();
        String alphaID = Mapper.getTokenID("creussalpha");
        String betaID = Mapper.getTokenID("creussbeta");
        String gammaID = Mapper.getTokenID("creussgamma");
        Wormholes onMap = wormholesOnMap(game, alphaID, betaID, gammaID);
        y += 25;
        xDeltaFromRight += onMap.allOnMap() ? 0 : 90;
        LawOverlays laws = new LawOverlays(
                ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban"),
                ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));

        if (!onMap.gamma()) {
            drawToken(ctx, slot, gammaID, xDeltaFromRight, y);
            xDeltaFromRight += TOKEN_STEP;
            y += onMap.alpha() || onMap.beta() ? 38 : 19;
        }
        if (!onMap.beta()) {
            drawToken(ctx, slot, betaID, xDeltaFromRight, y);
            drawLawOverlays(ctx.g2(), laws, "token_whalpha.png", slot.rightEdge() - xDeltaFromRight, y);
            xDeltaFromRight += TOKEN_STEP;
            y += onMap.alpha() || onMap.gamma() ? 38 : 19;
        }
        if (!onMap.alpha()) {
            drawToken(ctx, slot, alphaID, xDeltaFromRight, y);
            drawLawOverlays(ctx.g2(), laws, "token_whbeta.png", slot.rightEdge() - xDeltaFromRight, y);
            xDeltaFromRight += TOKEN_STEP;
        }

        xDeltaFromRight -= onMap.allOnMap() ? 0 : TOKEN_STEP;
        return xDeltaFromRight;
    }

    private static Wormholes wormholesOnMap(Game game, String alphaID, String betaID, String gammaID) {
        boolean alpha = false;
        boolean beta = false;
        boolean gamma = false;
        for (Tile tile : game.getTileMap().values()) {
            Set<String> tileTokens = tile.getUnitHolders().get("space").getTokenList();
            alpha |= tileTokens.contains(alphaID);
            beta |= tileTokens.contains(betaID);
            gamma |= tileTokens.contains(gammaID);
        }
        return new Wormholes(alpha, beta, gamma);
    }

    private static void drawToken(PlayerAreaContext ctx, AreaSlot slot, String tokenID, int xDeltaFromRight, int y) {
        String tokenFile = ImageResources.tokenOrAttachment(tokenID);
        if (ctx.game().isLiberationC4Mode()) {
            tokenFile = tokenFile.replace("token_creuss", "token_crimsoncreuss");
        }
        ctx.g2().drawImage(ImageHelper.read(tokenFile), slot.rightEdge() - xDeltaFromRight, y, null);
    }

    private static void drawLawOverlays(Graphics2D g2, LawOverlays laws, String doubledWormholeToken, int x, int y) {
        if (laws.travelBan()) {
            BufferedImage blocked = ImageHelper.read(
                    ImageResources.token("agenda_wormhole_blocked" + (laws.reconstruction() ? "_half" : "") + ".png"));
            g2.drawImage(blocked, x + 40, y + 40, null);
        }
        if (laws.reconstruction()) {
            BufferedImage doubled = ImageHelper.readScaled(ImageResources.token(doubledWormholeToken), 40.0f / 65);
            g2.drawImage(doubled, x, y, null);
        }
    }
}
