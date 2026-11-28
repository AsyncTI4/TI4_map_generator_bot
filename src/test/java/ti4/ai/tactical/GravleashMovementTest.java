package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Gravleash Maneuvers (the Letnev breakthrough) gives every non-fighter ship the highest move value among the ships
// that move with it, and Gravity Drive on the fastest ship carries the whole group one system further.
class GravleashMovementTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private TacticalPlanner.Context context;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
    }

    private Tile systemsAway(int steps) {
        List<String> visited = new ArrayList<>(List.of(AiTestGame.HOME));
        String position = AiTestGame.HOME;
        for (int step = 0; step < steps; step++) {
            List<String> seen = List.copyOf(visited);
            boolean outward = step > 0;
            position = MapPositionMapper.getAdjacentTilePositions(position).stream()
                    .filter(candidate -> !"x".equals(candidate) && !seen.contains(candidate))
                    .filter(candidate -> !outward
                            || MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                                    .noneMatch(candidate::equals))
                    .findFirst()
                    .orElseThrow();
            visited.add(position);
            test.place("26", position);
        }
        return test.game.getTileByPosition(position);
    }

    private void unlockBreakthrough() {
        test.nekro.addBreakthrough("letnevbt");
        test.nekro.setBreakthroughUnlocked("letnevbt", true);
    }

    private TacticalPlanner.Context fresh() {
        return new TacticalPlanner.Context(test.game, test.nekro);
    }

    @Test
    void aSlowShipKeepsToItsOwnMoveWithoutTheBreakthrough() {
        Tile target = systemsAway(2);
        context = fresh();

        assertThat(context.linkedReaches(home, target, UnitType.Destroyer)).isTrue();
        assertThat(context.linkedReaches(home, target, UnitType.Dreadnought)).isFalse();
    }

    @Test
    void theBreakthroughLetsADreadnoughtTravelAtTheDestroyersSpeed() {
        unlockBreakthrough();
        Tile target = systemsAway(2);
        context = fresh();

        assertThat(context.linkedReaches(home, target, UnitType.Dreadnought)).isTrue();
    }

    @Test
    void gravityDriveOnTheFastShipCarriesTheWholeGroupAnotherSystem() {
        unlockBreakthrough();
        test.nekro.addTech("gd");
        Tile target = systemsAway(3);
        context = fresh();

        assertThat(context.linkedReaches(home, target, UnitType.Dreadnought)).isTrue();
    }

    @Test
    void withoutTheFastShipTheSlowOnesStayShort() {
        unlockBreakthrough();
        Tile target = systemsAway(2);
        context = fresh();
        List<UnitMove> dreadnoughtsAlone = List.of(new UnitMove(home.getPosition(), "space", UnitType.Dreadnought, 2));
        List<UnitMove> withDestroyer = List.of(
                new UnitMove(home.getPosition(), "space", UnitType.Dreadnought, 2),
                new UnitMove(home.getPosition(), "space", UnitType.Destroyer, 1));

        assertThat(context.carriesItsOwnSpeed(home, target, dreadnoughtsAlone)).isFalse();
        assertThat(context.carriesItsOwnSpeed(home, target, withDestroyer)).isTrue();
    }
}
