package ti4.helpers.adjacency;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class CustomLinkRule implements AdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        adjacent.addAll(listedBy(context, position));
    }

    @Override
    public void into(AdjacencyContext context, String position, Set<String> sources) {
        sources.addAll(primariesListing(context, position));
    }

    private static List<String> listedBy(AdjacencyContext context, String primary) {
        return context.getGame().getCustomAdjacentTiles().getOrDefault(primary, List.of());
    }

    private static Set<String> primariesListing(AdjacencyContext context, String position) {
        Map<String, List<String>> custom = context.getGame().getCustomAdjacentTiles();
        return custom.entrySet().stream()
                .filter(entry -> entry.getValue().contains(position))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
