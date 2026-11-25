package ti4.helpers.adjacency;

import java.util.Set;
import ti4.game.Player;
import ti4.service.map.RefractureRulesService;

final class RefractureFenrirRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Player player = context.getPlayer();
        if (player != null) {
            RefractureRulesService.addFenrirAdjacencies(context.getGame(), player, position, adjacent);
        }
    }
}
