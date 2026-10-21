package ti4.image.playerarea;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream.DreamUnitsHandler;
import ti4.game.Player;
import ti4.model.Mapper;

final class DreamNexusTokens {

    private static final int NEXUS_TOKENS = 3;

    private DreamNexusTokens() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (!player.hasAbility("dream_nexus")) {
            return xDeltaFromRight;
        }
        int remaining = Math.max(0, NEXUS_TOKENS - DreamUnitsHandler.getNexusTokenCount(ctx.game()));
        return TokenSupply.drawWideZigzag(
                ctx, slot, Mapper.getTokenID("beansnexus"), NEXUS_TOKENS, remaining, xDeltaFromRight, y);
    }
}
