package ti4.helpers.adjacency;

import java.util.Set;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream.DreamFactionTechHandler;

final class DreamNebulaRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        if (!context.isMovement()) return;
        adjacent.addAll(DreamFactionTechHandler.getNebulaAdjacencies(
                context.getGame(), context.getPlayer(), context.getGame().getTileByPosition(position)));
    }
}
