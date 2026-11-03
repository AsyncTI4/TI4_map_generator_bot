package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.FoWHelper;
import ti4.testUtils.BaseTi4Test;

// TODO: remove after 2026-12-31 together with LegacyAdjacency; it only guards the move to the Adjacency API.
// The Adjacency API must answer exactly what FoWHelper.getAdjacentTiles answered before it (frozen in
// LegacyAdjacency), for every flag combination callers use, every player and every tile.
class AdjacencyParityTest extends BaseTi4Test {

    private static final boolean[][] TO_SHOW_FOR_DISTANCE = {{false, false}, {true, false}, {false, true}};

    @ParameterizedTest
    @EnumSource(AdjacencyScenarios.class)
    void wrappersMatchTheLegacyImplementation(AdjacencyScenarios scenario) {
        try (var harness = TestGameHarness.fromSourceGame(scenario.fixture)) {
            Game game = harness.load();
            scenario.apply(game);

            List<String> mismatches = new ArrayList<>();
            for (Player player : AdjacencyScenarios.playersIncludingNone(game)) {
                for (boolean[] flags : TO_SHOW_FOR_DISTANCE) {
                    for (boolean includeTile : new boolean[] {true, false}) {
                        for (String position : AdjacencyScenarios.positions(game)) {
                            Set<String> legacy = LegacyAdjacency.getAdjacentTiles(
                                    game, position, player, flags[0], includeTile, flags[1]);
                            Set<String> current =
                                    FoWHelper.getAdjacentTiles(game, position, player, flags[0], includeTile, flags[1]);
                            if (!legacy.equals(current)) {
                                mismatches.add("%s toShow=%s forDistance=%s includeTile=%s %s legacy=%s current=%s"
                                        .formatted(
                                                player == null ? "null" : player.getFaction(),
                                                flags[0],
                                                flags[1],
                                                includeTile,
                                                position,
                                                new TreeSet<>(legacy),
                                                new TreeSet<>(current)));
                            }
                        }
                    }
                }
            }
            assertThat(mismatches).as(scenario.name()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(AdjacencyScenarios.class)
    void traverseMatchesTheLegacyWalk(AdjacencyScenarios scenario) {
        try (var harness = TestGameHarness.fromSourceGame(scenario.fixture)) {
            Game game = harness.load();
            scenario.apply(game);

            for (String position : AdjacencyScenarios.positions(game)) {
                for (boolean naturalMapOnly : new boolean[] {true, false}) {
                    assertThat(FoWHelper.traverseAdjacencies(game, naturalMapOnly, position))
                            .as(position + " naturalMapOnly=" + naturalMapOnly)
                            .isEqualTo(LegacyAdjacency.traverseAdjacencies(game, naturalMapOnly, position));
                }
            }
        }
    }
}
