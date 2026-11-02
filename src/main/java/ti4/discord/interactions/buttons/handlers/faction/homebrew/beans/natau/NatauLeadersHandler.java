package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CommandCounterHelper;

@UtilityClass
public class NatauLeadersHandler {

    public static boolean canMoveOutOfLockedAnomaly(Game game, Player player, Tile tile) {
        return tile != null
                && player.getLeader("natauhero").map(Leader::isActive).orElse(false)
                && tile.getTileModel().isAnomaly()
                && CommandCounterHelper.hasCC(player, tile);
    }
}
