package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ThreatsTest extends BaseTi4Test {

    private AiTestGame test;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    // A Sol dreadnought next door can move into Nekro's home; its own system counts too.
    @Test
    void anEnemyShipThreatensWhatItCanReach() {
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.sol, UnitType.Dreadnought, 1);

        assertThat(Threats.reachableByEnemies(test.game, test.nekro)).contains(AiTestGame.HOME, neighbour);
    }

    // Fighters cannot move on their own, and the seat's own ships never threaten it.
    @Test
    void fightersAndOwnShipsThreatenNothing() {
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.sol, UnitType.Fighter, 2);
        test.units(lodor, "space", test.nekro, UnitType.Dreadnought, 1);

        assertThat(Threats.reachableByEnemies(test.game, test.nekro)).isEmpty();
    }
}
