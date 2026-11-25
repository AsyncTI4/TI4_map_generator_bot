package ti4.helpers.adjacency;

import java.util.Set;

interface AdjacencyRule {
    void from(AdjacencyContext context, String position, Set<String> adjacent);

    void into(AdjacencyContext context, String position, Set<String> sources);
}
