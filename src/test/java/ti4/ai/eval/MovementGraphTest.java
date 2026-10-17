package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

class MovementGraphTest extends BaseTi4Test {

    private AiTestGame test;
    private String middle;
    private String beyond;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        middle = AiTestGame.neighbourOf(AiTestGame.HOME);
        beyond = PositionMapper.getAdjacentTilePositions(middle).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .filter(position -> !PositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                        .contains(position))
                .findFirst()
                .orElseThrow();
        Tile blocked = test.place("26", middle);
        test.place("25", beyond);
        test.units(blocked, "space", test.sol, UnitType.Destroyer, 1);
    }

    // Other players' ships stop movement, so with only one way through the far system is out of reach.
    @Test
    void stopsAtSystemsHoldingOtherPlayersShips() {
        assertThat(MovementGraph.reach(test.game, test.nekro, AiTestGame.HOME, 2))
                .doesNotContainKey(beyond);
    }

    // Light/Wave Deflector lets ships move through systems that contain other players' ships.
    @Test
    void movesThroughOtherPlayersShipsWithLightWaveDeflector() {
        test.nekro.addTech("lwd");

        assertThat(MovementGraph.reach(test.game, test.nekro, AiTestGame.HOME, 2))
                .containsEntry(beyond, 2);
    }
}
