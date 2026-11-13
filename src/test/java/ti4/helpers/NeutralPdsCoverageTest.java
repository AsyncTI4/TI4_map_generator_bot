package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class NeutralPdsCoverageTest extends BaseTi4Test {

    private Game game;
    private Player neutral;
    private Tile pdsSystem;
    private Tile adjacentSystem;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("Neutral PDS coverage");
        neutral = game.setupNeutralPlayer("gray");
        // Tile 19 is Wellon; 101 and 102 are adjacent on the map.
        pdsSystem = new Tile("19", "101");
        adjacentSystem = new Tile("38", "102");
        game.setTile(pdsSystem);
        game.setTile(adjacentSystem);
        pdsSystem.addUnit("wellon", Units.getUnitKey(UnitType.Pds, neutral.getColorID()), 1);
    }

    @Test
    void neutralPdsCoversOnlyItsOwnSystemAndHitsOnSix() {
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, pdsSystem)
                        .get("neutral")
                        .getDiceValues())
                .containsExactly(6);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, adjacentSystem)).isNull();
    }

    @Test
    void upgradedNeutralPdsReachesAdjacentSystemsAndHitsOnFive() {
        neutral.addTech("pds2");

        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, adjacentSystem)
                        .get("neutral")
                        .getDiceValues())
                .containsExactly(5);
    }
}
