package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Pirate Contract for Turn Their Fleets to Dust: post the neutral destroyer where our space cannon covers it, so that a
// zero-move activation can kill it. A system next to our planets is normally off limits (the destroyer would block our
// own lanes); the point it could score outweighs that, but not the cost of two raids, the card and the action.
class DustTargetTest extends BaseTi4Test {

    private static final String CARD = "piratecontract1";

    private CardTable table;
    private List<String> neighbours;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        neighbours = MapPositionMapper.getAdjacentTilePositions("301").stream()
                .filter(position -> !"x".equals(position))
                .toList();
    }

    private Optional<Plan> plan() {
        return AiActionCards.of(CARD).orElseThrow().planFor(table.game, table.nekro);
    }

    private Tile coveredSystem(String tileId, String planet, String position, int pds) {
        Tile tile = table.system(tileId, position);
        table.nekro.addPlanet(planet);
        table.test.units(tile, planet, table.nekro, UnitType.Pds, pds);
        return tile;
    }

    @Test
    void postsThePiratesUnderOurPdsWhileHoldingTheSecret() {
        table.nekro.setSecret("ttfd");
        coveredSystem("25", "quann", neighbours.get(0), 2);

        Plan plan = plan().orElseThrow();

        assertThat(plan.target()).isEqualTo(neighbours.get(0));
        assertThat(plan.value()).isGreaterThan(table.stall());
    }

    @Test
    void prefersTheCoveredSystemToAPureBlockingTarget() {
        table.nekro.setSecret("ttfd");
        table.system("40", "310");
        coveredSystem("25", "quann", neighbours.get(0), 2);

        assertThat(plan().orElseThrow().target()).isEqualTo(neighbours.get(0));
    }

    @Test
    void leavesTheSystemsNextToItsPlanetsAloneWithoutTheSecret() {
        coveredSystem("25", "quann", neighbours.get(0), 2);

        assertThat(plan()).isEmpty();
    }

    @Test
    void avoidsOurDockWhenAnEquallyCoveredSystemHasNone() {
        table.nekro.setSecret("ttfd");
        Tile docked = coveredSystem("25", "quann", neighbours.get(0), 2);
        table.test.units(docked, "quann", table.nekro, UnitType.Spacedock, 1);
        coveredSystem("26", "lodor", neighbours.get(1), 2);

        assertThat(plan().orElseThrow().target()).isEqualTo(neighbours.get(1));
    }

    @Test
    void neverPicksALoneDockSystem() {
        table.nekro.setSecret("ttfd");
        Tile docked = coveredSystem("25", "quann", neighbours.get(0), 2);
        table.test.units(docked, "quann", table.nekro, UnitType.Spacedock, 1);

        assertThat(plan()).isEmpty();
    }

    @Test
    void neverPicksASystemHoldingOurShips() {
        table.nekro.setSecret("ttfd");
        Tile covered = coveredSystem("25", "quann", neighbours.get(0), 2);
        table.ships(table.nekro, covered, UnitType.Destroyer, 1);

        assertThat(plan()).isEmpty();
    }

    @Test
    void skipsASingleWeakPdsThatCannotPayForTwoRaids() {
        table.nekro.setSecret("ttfd");
        coveredSystem("25", "quann", neighbours.get(0), 1);

        assertThat(plan()).isEmpty();
    }
}
