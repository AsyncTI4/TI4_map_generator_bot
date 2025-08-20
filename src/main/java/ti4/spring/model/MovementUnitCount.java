package ti4.spring.model;

import java.util.List;

/**
 * Represents a single unit move with counts per state.
 * counts: [regular, sustained]
 */
public record MovementUnitCount(String unitType, String colorID, List<Integer> counts) {}
