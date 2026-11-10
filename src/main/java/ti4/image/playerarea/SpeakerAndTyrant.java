package ti4.image.playerarea;

import java.awt.image.BufferedImage;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.draw.MegagameGroupTokens;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;
import ti4.service.megagame.MegagameGroupService;

final class SpeakerAndTyrant {

    private static final int TOKEN_WIDTH = 200;
    private static final int SPEAKER_BESIDE_TYRANT = 80;
    private static final int GROUP_BADGE_WIDTH = 50;

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
        if (MegagameGroupService.isActive(ctx.game())) {
            xDeltaFromRight = drawGroupMarkers(ctx, slot, player, xDeltaFromRight, y);
        }
        return xDeltaFromRight;
    }

    private static int drawGroupMarkers(
            PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        String group = MegagameGroupService.groupOf(ctx.game(), player);
        if (group == null) {
            return xDeltaFromRight;
        }
        boolean gameSpeaker = player.getUserID().equals(ctx.game().getSpeakerUserID());
        if (!gameSpeaker && MegagameGroupService.isGroupSpeaker(ctx.game(), player)) {
            BufferedImage token = MegagameGroupTokens.speakerToken(group);
            if (token != null) {
                xDeltaFromRight += player.isTyrant() ? SPEAKER_BESIDE_TYRANT : TOKEN_WIDTH;
                ctx.g2().drawImage(token, slot.rightEdge() - xDeltaFromRight, y + 25, null);
            }
        }
        xDeltaFromRight += GROUP_BADGE_WIDTH;
        MegagameGroupTokens.drawBadge(ctx.g2(), group, slot.rightEdge() - xDeltaFromRight, y + 25);
        return xDeltaFromRight;
    }

    private static void drawToken(PlayerAreaContext ctx, AreaSlot slot, String token, int xDeltaFromRight, int y) {
        String tokenFile = ImageResources.token(Mapper.getTokenID(token));
        if (tokenFile != null) {
            ctx.g2().drawImage(ImageHelper.read(tokenFile), slot.rightEdge() - xDeltaFromRight, y + 25, null);
        }
    }
}
