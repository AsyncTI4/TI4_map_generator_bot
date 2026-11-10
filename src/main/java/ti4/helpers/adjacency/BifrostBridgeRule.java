package ti4.helpers.adjacency;

import java.util.Set;
import ti4.service.planet.AsgardLegendaryService;

final class BifrostBridgeRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        AsgardLegendaryService.addBifrostBridgeAdjacencies(context.getGame(), context.getPlayer(), position, adjacent);
    }
}
