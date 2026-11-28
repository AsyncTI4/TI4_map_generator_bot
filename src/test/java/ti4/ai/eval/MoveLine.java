package ti4.ai.eval;

import java.util.ArrayList;
import java.util.List;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;

/**
 * A straight corridor of systems leading away from Nekro's home, for movement tests. Only the placed systems exist, so
 * the corridor is the only path and the n-th system is exactly n moves from home. Every step is a planet system
 * without wormholes unless a test swaps one for an anomaly.
 */
final class MoveLine {

    private static final List<String> PLAIN_SYSTEMS = List.of("19", "20", "21", "22", "23", "24", "27", "28");

    final AiTestGame test = new AiTestGame();
    final Tile home;
    final List<String> positions = new ArrayList<>();

    MoveLine(int length) {
        home = test.nekroHome();
        positions.add(AiTestGame.HOME);
        for (int step = 1; step <= length; step++) {
            String next = nextPosition();
            positions.add(next);
            test.place(PLAIN_SYSTEMS.get(step - 1), next);
        }
    }

    /** The system {@code step} moves from home; step 0 is home itself. */
    Tile at(int step) {
        return test.game.getTileByPosition(positions.get(step));
    }

    /** Replaces the system at {@code step} with another system tile, such as 42 (a nebula). */
    Tile swap(int step, String tileId) {
        return test.place(tileId, positions.get(step));
    }

    /** A board position that is neither on the corridor nor next to it. */
    String islandPosition() {
        for (int ring = 1; ring <= 3; ring++) {
            for (int index = 1; index <= ring * 6; index++) {
                String candidate = ring + String.format("%02d", index);
                boolean touches = positions.stream()
                        .anyMatch(step -> step.equals(candidate)
                                || MapPositionMapper.getAdjacentTilePositions(step)
                                        .contains(candidate));
                if (!touches) return candidate;
            }
        }
        throw new IllegalStateException("no free position");
    }

    void ships(int step, UnitType type, int count) {
        test.units(at(step), "space", test.nekro, type, count);
    }

    private String nextPosition() {
        String last = positions.getLast();
        return MapPositionMapper.getAdjacentTilePositions(last).stream()
                .filter(candidate -> !"x".equals(candidate) && !positions.contains(candidate))
                .filter(candidate -> positions.stream()
                        .limit(positions.size() - 1L)
                        .noneMatch(earlier -> MapPositionMapper.getAdjacentTilePositions(earlier)
                                .contains(candidate)))
                .findFirst()
                .orElseThrow();
    }
}
