package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

/**
 * JMSC cards care about "more than 5 hexes away". The ruling is adjacency distance on the map, so anomalies and
 * other players' ships never lengthen it. The fixture is a full three-ring board of plain systems.
 */
class ThirtyKDistanceTest extends BaseTi4Test {

    private static final String PLAIN_SYSTEM = "19";
    private static final String SUPERNOVA = "43";

    private Game game;

    @BeforeEach
    void setUpThreeRingBoard() {
        game = new Game();
        game.setName("thirtyk-distance-test");
        game.setTile(new Tile(PLAIN_SYSTEM, "000"));
        for (int ring = 1; ring <= 3; ring++) {
            for (int index = 1; index <= ring * 6; index++) {
                String position = String.format("%d%02d", ring, index);
                game.setTile(new Tile(PLAIN_SYSTEM, position));
            }
        }
    }

    @Test
    void onlyOppositeCornersOfTheOuterRingAreMoreThanFiveApart() {
        assertThat(ThirtyKDistance.isMoreThanFiveAway(game, "301", "310")).isTrue();
        assertThat(ThirtyKDistance.isMoreThanFiveAway(game, "301", "304")).isFalse();
        assertThat(ThirtyKDistance.isMoreThanFiveAway(game, "301", "301")).isFalse();
        assertThat(ThirtyKDistance.isMoreThanFiveAway(game, null, "301")).isFalse();
    }

    @Test
    void anomaliesDoNotBlockTheCount() {
        // A supernova on Mecatol's spot would block movement, but distance here is plain adjacency.
        game.setTile(new Tile(SUPERNOVA, "000"));
        assertThat(ThirtyKDistance.isMoreThanFiveAway(game, "301", "304")).isFalse();
        assertThat(ThirtyKDistance.isMoreThanFiveAway(game, "101", "104")).isFalse();
    }
}
