package ti4.image.map;

import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import ti4.game.Player;
import ti4.image.draw.MegagameGroupTokens;
import ti4.service.megagame.MegagameGroupService;

final class SpeakerOffset {

    private SpeakerOffset() {}

    static int of(Player player) {
        return player.isSpeaker() || groupSpeakerToken(player) != null ? 30 : 0;
    }

    @Nullable
    static BufferedImage groupSpeakerToken(Player player) {
        if (player.isSpeaker() || !MegagameGroupService.isActive(player.getGame())) {
            return null;
        }
        if (!MegagameGroupService.isGroupSpeaker(player.getGame(), player)) {
            return null;
        }
        return MegagameGroupTokens.speakerToken(MegagameGroupService.groupOf(player.getGame(), player));
    }
}
