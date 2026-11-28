package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Each onward leg from a staging system is its own tactical action, so Gravity Drive gives each leg its +1 again.
// The board is a single lane of empty systems from Nekro's home, so the n-th system is exactly n moves away; Nekro's
// carriers move 1.
class GravityDriveOnwardLegsTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private final List<Tile> lane = new ArrayList<>();

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        String position = AiTestGame.HOME;
        for (int step = 0; step < 4; step++) {
            String previous = position;
            position = MapPositionMapper.getAdjacentTilePositions(previous).stream()
                    .filter(candidate -> !"x".equals(candidate))
                    .filter(candidate -> test.game.getTileByPosition(candidate) == null)
                    .filter(candidate -> MapPositionMapper.getAdjacentTilePositions(candidate).stream()
                            .noneMatch(next -> !next.equals(previous) && test.game.getTileByPosition(next) != null))
                    .findFirst()
                    .orElseThrow();
            lane.add(test.place("46", position));
        }
    }

    private TacticalPlanner.Context context() {
        return new TacticalPlanner.Context(test.game, test.nekro);
    }

    @Test
    void withoutGravityDriveTwoLegsCoverTwoSystems() {
        TacticalPlanner.Context context = context();

        assertThat(context.onwardRange(home, lane.get(3), UnitType.Carrier, 2)).isEqualTo(2);
        assertThat(context.reachesOnward(home, lane.get(1), UnitType.Carrier, 2))
                .isTrue();
        assertThat(context.reachesOnward(home, lane.get(2), UnitType.Carrier, 2))
                .isFalse();
    }

    // Gathering used to count no Gravity Drive on onward legs, so it disagreed with the staging feasibility check.
    @Test
    void gravityDriveAddsOneOnEveryOnwardLeg() {
        test.nekro.addTech("gd");
        TacticalPlanner.Context context = context();

        assertThat(context.onwardRange(home, lane.get(3), UnitType.Carrier, 1)).isEqualTo(2);
        assertThat(context.onwardRange(home, lane.get(3), UnitType.Carrier, 2)).isEqualTo(4);
        assertThat(context.reachesOnward(home, lane.get(3), UnitType.Carrier, 2))
                .isTrue();
    }

    // A stall step or a forward transport move must not spend the once-per-round Ionian Fuel Refinery: only the boosts
    // that come back every tactical action (Gravity Drive, Imperator) count for those low-value moves.
    @Test
    void lowValueMovesLeaveTempestaForTheMoveThatNeedsIt() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.nekro.addPlanet("tempesta");
        TacticalPlanner.Context context = context();

        assertThat(context.reachesWithGravityDrive(home, lane.get(1), UnitType.Carrier))
                .isTrue();
        assertThat(context.reachesWithEveryActionBoosts(home, lane.get(1), UnitType.Carrier))
                .isFalse();

        test.nekro.addTech("gd");
        TacticalPlanner.Context withGravityDrive = context();
        assertThat(withGravityDrive.reachesWithEveryActionBoosts(home, lane.get(1), UnitType.Carrier))
                .isTrue();
        assertThat(withGravityDrive.reachesWithEveryActionBoosts(home, lane.get(2), UnitType.Carrier))
                .isFalse();
    }
}
