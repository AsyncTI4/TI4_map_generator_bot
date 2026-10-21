package ti4.image.playerarea;

import ti4.game.Player;

@FunctionalInterface
interface StripBlock {
    int draw(PlayerAreaContext ctx, Player player, int x, int y);
}
