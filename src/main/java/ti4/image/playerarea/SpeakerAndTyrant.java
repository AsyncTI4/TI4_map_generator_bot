package ti4.image.playerarea;

import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class SpeakerAndTyrant {

    private static final int TOKEN_WIDTH = 200;
    private static final int SPEAKER_BESIDE_TYRANT = 80;

    private SpeakerAndTyrant() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (player.isTyrant()) {
            xDeltaFromRight += TOKEN_WIDTH;
            drawToken(ctx, slot, Constants.TYRANT, xDeltaFromRight, y);
        }
        if (player.getUserID().equals(ctx.game().getSpeakerUserID())) {
            xDeltaFromRight += player.isTyrant() ? SPEAKER_BESIDE_TYRANT : TOKEN_WIDTH;
            drawToken(ctx, slot, Constants.SPEAKER, xDeltaFromRight, y);
        }
        return xDeltaFromRight;
    }

    private static void drawToken(PlayerAreaContext ctx, AreaSlot slot, String token, int xDeltaFromRight, int y) {
        String tokenFile = ImageResources.token(Mapper.getTokenID(token));
        if (tokenFile != null) {
            ctx.g2().drawImage(ImageHelper.read(tokenFile), slot.rightEdge() - xDeltaFromRight, y + 25, null);
        }
    }
}
