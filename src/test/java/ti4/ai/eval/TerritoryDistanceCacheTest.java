package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.Territory.Zone;
import ti4.game.Tile;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Distances on the hex grid depend only on the board positions, so they are walked once per start and kept for good;
// building a seat's territory again must not walk the grid again or change any zone.
class TerritoryDistanceCacheTest extends BaseTi4Test {

    @Test
    void buildingTheTerritoryAgainReusesTheWalkedDistances() {
        AiTestGame test = new AiTestGame();
        test.place(Mapper.getFaction("nekro").getHomeSystem(), "301");
        test.place("18", "000");
        Tile lane = new Tile("26", "201");

        Zone first = Territory.of(test.game, test.nekro).zone(lane);
        int walked = Territory.cachedStarts();
        Zone again = Territory.of(test.game, test.nekro).zone(lane);

        assertThat(walked).isPositive();
        assertThat(Territory.cachedStarts()).isEqualTo(walked);
        assertThat(again).isEqualTo(first);
    }
}
