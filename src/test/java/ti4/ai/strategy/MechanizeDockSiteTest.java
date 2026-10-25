package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Lodor (3 resources) sits alone in its system; Sakulag (2) shares one with Lazar. A forward dock on Lodor is richer,
// but while Mechanize the Military is held the dock goes to Sakulag: mechs it builds can be lifted onto Lazar.
class MechanizeDockSiteTest extends BaseTi4Test {

    private AiTestGame test;
    private List<String> planets;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        List<String> around = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .toList();
        test.place("26", around.get(0));
        test.place("31", around.get(1));
        for (String planet : List.of("lodor", "sakulag", "lazar")) test.nekro.addPlanet(planet);
        planets = List.of("lodor", "sakulag");
    }

    @Test
    void prefersADockSiteWithASecondPlanetWhileMechanizeIsHeld() {
        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.SPACE_DOCK, planets))
                .hasValue("lodor");

        test.nekro.setSecret("mtm");

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.SPACE_DOCK, planets))
                .hasValue("sakulag");
    }
}
