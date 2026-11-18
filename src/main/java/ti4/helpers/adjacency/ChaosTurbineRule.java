package ti4.helpers.adjacency;

import java.util.Set;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau.NatauAbilityHandler;

final class ChaosTurbineRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        if (!context.isMovement()) return;
        NatauAbilityHandler.addChaosTurbineAdjacencies(context.getGame(), context.getPlayer(), position, adjacent);
    }
}
