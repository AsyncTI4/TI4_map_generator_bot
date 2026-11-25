package ti4.helpers.adjacency;

import java.util.Set;

interface SymmetricAdjacencyRule extends AdjacencyRule {
    @Override
    default void into(AdjacencyContext context, String position, Set<String> sources) {
        from(context, position, sources);
    }
}
