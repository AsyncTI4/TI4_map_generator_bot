package ti4.image.playerarea;

import ti4.game.Player;

@FunctionalInterface
interface RightBlock {
    int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y);
}
