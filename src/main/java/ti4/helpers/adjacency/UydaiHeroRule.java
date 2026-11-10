package ti4.helpers.adjacency;

import java.util.Set;
import ti4.service.leader.UydaiHeroService;

final class UydaiHeroRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        if (context.isVision()) return;
        UydaiHeroService.addAdjacencies(context.getGame(), context.getPlayer(), position, adjacent);
    }
}
