package ti4.image.map;

import java.util.Set;

public record MapFocus(String faction, Set<String> litPositions) {

    public MapFocus {
        litPositions = Set.copyOf(litPositions);
    }

    boolean isDimmed(String position) {
        return !litPositions.contains(position);
    }
}
