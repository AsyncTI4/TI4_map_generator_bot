package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// A holder of Occupy the Seat of the Empire with three ships on Mecatol Rex keeps them there: moves that do not weigh
// the objective themselves (picking up stranded infantry, joining another fleet) pay for the point they would break.
class SeatStackTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    private AiTestGame test;
    private Tile rex;
    private List<String> ring;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.nekroHome();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(4);
        rex = test.place("18", MECATOL);
        test.nekro.addPlanet("mr");
        ring = MapPositionMapper.getAdjacentTilePositions(MECATOL).stream()
                .filter(position -> !"x".equals(position))
                .toList();
        test.units(rex, "space", test.nekro, UnitType.Carrier, 1);
        test.units(rex, "space", test.nekro, UnitType.Destroyer, 2);
    }

    private Tile strandedInfantry() {
        Tile strand = test.place("39", ring.get(0));
        test.units(strand, "space", test.nekro, UnitType.Infantry, 2);
        return strand;
    }

    private Optional<TacticalPlan> pickup(Tile strand) {
        return TacticalPlanner.forTarget(test.game, test.nekro, strand.getPosition())
                .filter(plan -> plan.kind() == TacticalPlan.Kind.PICKUP);
    }

    @Test
    void pickupLeavesTheStackWithoutTheSecret() {
        Tile strand = strandedInfantry();

        assertThat(pickup(strand)).isPresent();
    }

    @Test
    void pickupDoesNotBreakTheStackWhileTheSecretIsHeld() {
        test.nekro.setSecret("ose");
        Tile strand = strandedInfantry();

        Optional<TacticalPlan> plan = pickup(strand);

        assertThat(plan.map(TacticalPlan::score).orElse(-VP)).isLessThan(-VP / 2);
    }
}
