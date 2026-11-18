package ti4.helpers.adjacency;

public enum AdjacencyPurpose {
    VISION,
    MOVEMENT,
    EFFECTS;

    public static AdjacencyPurpose fromLegacyFlags(boolean toShow, boolean forDistance) {
        if (toShow) return VISION;
        return forDistance ? MOVEMENT : EFFECTS;
    }
}
