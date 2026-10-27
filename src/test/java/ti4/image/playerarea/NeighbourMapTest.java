package ti4.image.playerarea;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.TestGameHarness;
import ti4.testUtils.BaseTi4Test;

class NeighbourMapTest extends BaseTi4Test {

    @Test
    void equivalentsMatchPlayerNeighboursOnTheDefaultMap() {
        assertMatchesPlayerNeighbours(TestGameHarness.forDefaultMap());
    }

    @Test
    void equivalentsMatchPlayerNeighboursOnTheBorderAnomalyMap() {
        assertMatchesPlayerNeighbours(TestGameHarness.fromSourceGame("game-with-border-anomalies"));
    }

    @Test
    void equivalentsMatchPlayerNeighboursOnTheDisplacedUnitsMap() {
        assertMatchesPlayerNeighbours(TestGameHarness.fromSourceGame("game-with-displaced-units"));
    }

    @Test
    void neighboursAreComputedOncePerPlayer() {
        try (TestGameHarness harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            NeighbourMap neighbours = new NeighbourMap(game);
            Player player = game.getRealPlayers().getFirst();

            assertThat(neighbours.withEquivalents(player)).isSameAs(neighbours.withEquivalents(player));
            assertThat(neighbours.direct(player)).isSameAs(neighbours.direct(player));
        }
    }

    private static void assertMatchesPlayerNeighbours(TestGameHarness harness) {
        try (harness) {
            Game game = harness.load();
            NeighbourMap neighbours = new NeighbourMap(game);
            List<Player> realPlayers = game.getRealPlayers();
            assertThat(realPlayers.size()).isGreaterThanOrEqualTo(3);

            int playersWithNeighbours = 0;
            for (Player player : game.getRealAndEliminatedPlayers()) {
                Set<Player> expected = player.getNeighbouringPlayers(true);
                Set<Player> actual = neighbours.withEquivalents(player);

                assertThat(actual).as("equivalents of %s", player.getFaction()).isEqualTo(expected);
                assertThat(neighbours.direct(player))
                        .as("direct neighbours of %s", player.getFaction())
                        .isEqualTo(player.getNeighbouringPlayers(false));
                assertThat(new ArrayList<>(actual))
                        .as("order of %s", player.getFaction())
                        .isEqualTo(realPlayers.stream().filter(actual::contains).toList());
                if (!actual.isEmpty()) playersWithNeighbours++;
            }
            assertThat(playersWithNeighbours).isGreaterThanOrEqualTo(3);
        }
    }
}
