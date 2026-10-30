package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Planet;
import ti4.testUtils.BaseTi4Test;

// A dock on Mecatol Rex builds the fighters that make the three ships of Occupy the Seat of the Empire on the spot, so
// the holder who controls the planet counts it as a site.
class SeatDockSiteTest extends BaseTi4Test {

    private AiTestGame test;
    private Planet rex;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.place("18", "000");
        test.nekro.addPlanet("mr");
        rex = test.game.getPlanetsInfo().get("mr");
    }

    @Test
    void mecatolIsNoDockSiteWithoutTheSecret() {
        assertThat(StructurePolicy.isDockSite(test.game, test.nekro, rex)).isFalse();
    }

    @Test
    void mecatolIsADockSiteWhileTheSecretIsHeld() {
        test.nekro.setSecret("ose");

        assertThat(StructurePolicy.isDockSite(test.game, test.nekro, rex)).isTrue();
    }

    @Test
    void mecatolIsNoDockSiteOnceTheSeatLosesIt() {
        test.nekro.setSecret("ose");
        test.nekro.removePlanet("mr");

        assertThat(StructurePolicy.isDockSite(test.game, test.nekro, rex)).isFalse();
    }
}
