package ti4.helpers.adjacency;

import java.util.Set;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaCommanderHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.modules.homebrew.beans.NatauAgent;

final class TreatedAsAdjacentRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        if (player == null) return;
        for (String tilePosition : game.getTileMap().keySet()) {
            if (SarcosaCommanderHandler.treatsAsAdjacent(game, player, position, tilePosition)
                    || NatauAgent.treatsAsAdjacent(game, player, position, tilePosition)) {
                adjacent.add(tilePosition);
            }
        }
    }
}
