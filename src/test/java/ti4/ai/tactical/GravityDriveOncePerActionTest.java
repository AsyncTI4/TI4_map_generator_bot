package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Gravity Drive gives +1 move to one ship per tactical action. With Gravleash Maneuvers (the Letnev breakthrough) the
// boost spreads to the ships moving from the same system, but it is still spent once: two starting systems that each
// need it cannot both reach. In game aiplay1 the Letnev seat moved two such groups into Styx in one action.
// The board: Sol's fleet in New Albion/Starpoint, and two lanes of empty systems that meet only there, so each of the
// two Nekro fleets is exactly three systems away from the target and the lanes never shorten each other.
class GravityDriveOncePerActionTest extends BaseTi4Test {

    private static final String TARGET = "104";

    private AiTestGame test;
    private Tile target;
    private Tile first;
    private Tile second;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(4);
        test.nekroHome();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(12);
        target = test.place("27", TARGET);
        first = lane(TARGET, 3);
        second = lane(TARGET, 3);
        test.units(target, "space", test.sol, UnitType.Cruiser, 2);
        test.units(target, "space", test.sol, UnitType.Destroyer, 2);
        test.points(test.sol, 6);
        test.nekro.setSecret("sar");
        test.nekro.addTech("gd");
    }

    private Tile lane(String from, int steps) {
        String position = from;
        for (int step = 0; step < steps; step++) {
            String previous = position;
            position = MapPositionMapper.getAdjacentTilePositions(previous).stream()
                    .filter(candidate -> !"x".equals(candidate))
                    .filter(candidate -> test.game.getTileByPosition(candidate) == null)
                    .filter(candidate -> MapPositionMapper.getAdjacentTilePositions(candidate).stream()
                            .noneMatch(next -> !next.equals(previous) && test.game.getTileByPosition(next) != null))
                    .findFirst()
                    .orElseThrow();
            test.place("46", position);
        }
        return test.game.getTileByPosition(position);
    }

    private void unlockGravleash() {
        test.nekro.addBreakthrough("letnevbt");
        test.nekro.setBreakthroughUnlocked("letnevbt", true);
    }

    private void letnevFleets() {
        test.units(first, "space", test.nekro, UnitType.Carrier, 1);
        test.units(first, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(second, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(second, "space", test.nekro, UnitType.Cruiser, 1);
        test.units(second, "space", test.nekro, UnitType.Carrier, 1);
        test.units(second, "space", test.nekro, UnitType.Destroyer, 1);
    }

    private TacticalPlanner.Context context() {
        return new TacticalPlanner.Context(test.game, test.nekro);
    }

    private Optional<TacticalPlan> attack() {
        return TacticalPlanner.forTarget(test.game, test.nekro, TARGET).filter(plan -> plan.kind() == Kind.ATTACK);
    }

    private static long startingSystems(TacticalPlan plan) {
        return plan.moves().stream()
                .filter(move -> move.type() != UnitType.Fighter)
                .map(UnitMove::origin)
                .filter(origin -> !TARGET.equals(origin))
                .distinct()
                .count();
    }

    private static UnitMove ship(Tile origin, UnitType type, int count) {
        return new UnitMove(origin.getPosition(), "space", type, count);
    }

    @Test
    void theLanesAreThreeSystemsLong() {
        TacticalPlanner.Context context = context();

        assertThat(context.distance(first, target, UnitType.Destroyer)).isEqualTo(3);
        assertThat(context.distance(second, target, UnitType.Destroyer)).isEqualTo(3);
    }

    @Test
    void gravleashSpendsOneBoostPerStartingSystem() {
        unlockGravleash();
        letnevFleets();
        TacticalPlanner.Context context = context();
        List<UnitMove> oneGroup = List.of(ship(second, UnitType.Dreadnought, 1), ship(second, UnitType.Destroyer, 1));
        List<UnitMove> twoGroups = List.of(
                ship(first, UnitType.Carrier, 1),
                ship(first, UnitType.Destroyer, 1),
                ship(second, UnitType.Dreadnought, 1),
                ship(second, UnitType.Destroyer, 1));

        assertThat(context.moveBoostsNeeded(target, oneGroup)).isEqualTo(1);
        assertThat(context.withinMoveBoosts(target, oneGroup)).isTrue();
        assertThat(context.moveBoostsNeeded(target, twoGroups)).isEqualTo(2);
        assertThat(context.withinMoveBoosts(target, twoGroups)).isFalse();
    }

    @Test
    void withoutGravleashEveryShortShipNeedsItsOwnBoost() {
        TacticalPlanner.Context context = context();

        assertThat(context.moveBoostsNeeded(target, List.of(ship(first, UnitType.Destroyer, 1))))
                .isEqualTo(1);
        assertThat(context.moveBoostsNeeded(target, List.of(ship(first, UnitType.Destroyer, 2))))
                .isEqualTo(2);
        assertThat(context.moveBoostsNeeded(
                        target, List.of(ship(first, UnitType.Destroyer, 1), ship(second, UnitType.Destroyer, 1))))
                .isEqualTo(2);
    }

    @Test
    void gravleashAttackMovesFromOnlyOneBoostedSystem() {
        unlockGravleash();
        letnevFleets();

        TacticalPlan plan = attack().orElseThrow();

        assertThat(startingSystems(plan)).isEqualTo(1);
        assertThat(context().withinMoveBoosts(target, plan.moves())).isTrue();
    }

    @Test
    void gravleashCandidatesNeverCombineTwoBoostedSystems() {
        unlockGravleash();
        letnevFleets();
        TacticalPlanner.Context context = context();

        List<TacticalPlanner.Candidate> pool = TacticalPlanner.candidatePool(context, target, test.sol);

        assertThat(pool).isNotEmpty();
        assertThat(pool).allMatch(candidate -> context.withinMoveBoosts(target, candidate.moves()));
    }

    @Test
    void aSingleGravleashGroupStillReachesWithTheBoost() {
        unlockGravleash();
        test.units(second, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(second, "space", test.nekro, UnitType.Cruiser, 1);
        test.units(second, "space", test.nekro, UnitType.Destroyer, 1);

        TacticalPlan plan = attack().orElseThrow();

        assertThat(plan.moves())
                .filteredOn(move -> move.type() == UnitType.Dreadnought)
                .extracting(UnitMove::count)
                .containsExactly(2);
    }

    // A lone Sol destroyer, and one destroyer (move 2) in each lane: each needs Gravity Drive, so only one can come.
    private void loneDestroyers() {
        target.getUnitHolders().get("space").removeAllUnits(test.sol.getColor());
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
        test.units(first, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(second, "space", test.nekro, UnitType.Destroyer, 1);
    }

    @Test
    void withoutGravleashOnlyOneShipIsBoosted() {
        loneDestroyers();

        // Without the cap this attacked with both destroyers; one destroyer alone may not be worth the activation.
        attack().ifPresent(plan -> {
            assertThat(startingSystems(plan)).isEqualTo(1);
            assertThat(context().moveBoostsNeeded(target, plan.moves())).isEqualTo(1);
        });
    }

    @Test
    void withoutGravleashNoCandidateBoostsTwoShips() {
        loneDestroyers();
        TacticalPlanner.Context context = context();

        List<TacticalPlanner.Candidate> pool = TacticalPlanner.candidatePool(context, target, test.sol);

        assertThat(pool).isNotEmpty();
        assertThat(pool).allMatch(candidate -> context.withinMoveBoosts(target, candidate.moves()));
    }
}
