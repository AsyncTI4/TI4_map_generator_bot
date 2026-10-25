package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Moving a fleet into range of a target it cannot yet reach: the fleet waits one system away, where it can strike next
// round.
class StagingTest extends BaseTi4Test {

    private static final String TARGET = DefenseScenario.SARDAKK_HOME_POSITION;

    private DefenseScenario board;
    private String launchPosition;
    private String stagingPosition;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        board.reveal("sway_council");
        board.sardakk.setTg(8);
        board.test.game.scorePublicObjective(board.sardakk.getUserID(), board.test.game.addCustomPO("Nearly there", 9));
        Tile sardakkHome = board.test.game.getTileByPosition(TARGET);
        board.test.units(sardakkHome, "quinarra", board.sardakk, UnitType.Infantry, 3);
        pickLaunchAndStaging();
        Tile launch = board.test.place("47", launchPosition);
        board.test.units(launch, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(launch, "space", board.test.nekro, UnitType.Dreadnought, 1);
        board.test.units(launch, "space", board.test.nekro, UnitType.Infantry, 4);
        board.test.place("48", stagingPosition);
    }

    private static List<String> neighbours(String position) {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate))
                .toList();
    }

    private boolean free(String position) {
        return board.test.game.getTileByPosition(position) == null;
    }

    private void pickLaunchAndStaging() {
        List<String> nearTarget = neighbours(TARGET);
        for (String staging : nearTarget) {
            if (!free(staging)) continue;
            for (String launch : neighbours(staging)) {
                boolean twoAway = !nearTarget.contains(launch) && !TARGET.equals(launch);
                if (twoAway && free(launch)) {
                    stagingPosition = staging;
                    launchPosition = launch;
                    return;
                }
            }
        }
        throw new IllegalStateException("no two-step approach to " + TARGET);
    }

    private Optional<TacticalPlan> stagePlan() {
        return TacticalPlanner.ranked(board.test.game, board.test.nekro).stream()
                .filter(plan -> plan.kind() == Kind.STAGE)
                .findFirst();
    }

    // Test 20: the neighbour is about to win and its home is two moves away for a fleet that moves one. Nothing can be
    // attacked now, so the fleet moves to the empty system between, from where it can strike next round.
    @Test
    void movesAFleetIntoRangeOfAWinningNeighboursHome() {
        assertThat(TacticalPlanner.forTarget(board.test.game, board.test.nekro, TARGET)
                        .filter(plan -> plan.kind() == Kind.ATTACK))
                .isEmpty();

        Optional<TacticalPlan> plan = stagePlan();

        assertThat(plan).isPresent();
        assertThat(plan.get().target()).isEqualTo(stagingPosition);
        assertThat(plan.get().landings()).isEmpty();
        assertThat(plan.get().moves()).allMatch(move -> move.origin().equals(launchPosition));
        assertThat(TacticalRules.describe(plan.get())).isEqualTo("stage " + stagingPosition);
    }

    @Test
    void stagePlansNeverComeFromTheSingleTargetEntryPoints() {
        assertThat(TacticalPlanner.forTarget(board.test.game, board.test.nekro, stagingPosition)
                        .filter(plan -> plan.kind() == Kind.STAGE))
                .isEmpty();
        assertThat(TacticalPlanner.bestForWarfare(board.test.game, board.test.nekro)
                        .filter(plan -> plan.kind() == Kind.STAGE))
                .isEmpty();
    }

    // A strong enemy fleet next to the staging system could strike the staged fleet first.
    @Test
    void doesNotStageIntoAStrongCounterstrike() {
        double calm = stagePlan().orElseThrow().score();
        String hunter = neighbours(stagingPosition).stream()
                .filter(position -> free(position) && !neighbours(TARGET).contains(position))
                .findFirst()
                .orElseThrow();
        Tile lair = board.test.place("49", hunter);
        board.test.units(lair, "space", board.sardakk, UnitType.Dreadnought, 3);
        board.test.units(lair, "space", board.sardakk, UnitType.Carrier, 1);
        board.sardakk.setFleetCC(5);

        Optional<TacticalPlan> threatened = stagePlan();

        assertThat(threatened.map(TacticalPlan::score).orElse(0.0)).isLessThan(calm);
    }

    // The neighbour's reinforcements next round are part of the fight the staged fleet would meet, so enough of them
    // make the later strike not worth preparing.
    @Test
    void doesNotStageAgainstReinforcementsThatWouldBeatTheFleet() {
        String reserve = neighbours(TARGET).stream()
                .filter(position ->
                        free(position) && !neighbours(stagingPosition).contains(position))
                .findFirst()
                .orElseThrow();
        Tile barracks = board.test.place("49", reserve);
        board.test.units(barracks, "space", board.sardakk, UnitType.Dreadnought, 3);
        board.test.units(barracks, "space", board.sardakk, UnitType.Carrier, 1);
        board.test.units(barracks, "space", board.sardakk, UnitType.Infantry, 4);
        board.sardakk.setFleetCC(6);

        assertThat(stagePlan()).isEmpty();
    }

    private String secondLaunchPosition() {
        return neighbours(stagingPosition).stream()
                .filter(position -> free(position)
                        && !TARGET.equals(position)
                        && !neighbours(TARGET).contains(position)
                        && !launchPosition.equals(position))
                .findFirst()
                .orElseThrow();
    }

    private String secondLaunch(int dreadnoughts) {
        String position = secondLaunchPosition();
        Tile launch = board.test.place("49", position);
        board.test.units(launch, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(launch, "space", board.test.nekro, UnitType.Dreadnought, dreadnoughts);
        board.test.units(launch, "space", board.test.nekro, UnitType.Infantry, 4);
        return position;
    }

    private void defendTheTargetWith(int dreadnoughts, int cruisers) {
        Tile sardakkHome = board.test.game.getTileByPosition(TARGET);
        board.test.units(sardakkHome, "space", board.sardakk, UnitType.Dreadnought, dreadnoughts);
        board.test.units(sardakkHome, "space", board.sardakk, UnitType.Cruiser, cruisers);
    }

    private static Set<String> originsOf(TacticalPlan plan) {
        return plan.moves().stream().map(TacticalPlan.UnitMove::origin).collect(Collectors.toSet());
    }

    private static int shipsMoved(TacticalPlan plan) {
        return plan.moves().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(TacticalPlan.UnitMove::count)
                .sum();
    }

    private List<TacticalPlan> stagePlans() {
        return TacticalPlanner.ranked(board.test.game, board.test.nekro).stream()
                .filter(plan -> plan.kind() == Kind.STAGE)
                .toList();
    }

    // The home is defended by a dreadnought and a cruiser, which one fleet (a carrier and a dreadnought) cannot beat
    // next round. A second fleet behind the same staging system makes the strike strong enough, so both move there.
    @Test
    void gathersTwoFleetsThatAreEachTooWeakAlone() {
        defendTheTargetWith(1, 1);
        assertThat(stagePlan()).isEmpty();
        String second = secondLaunch(1);

        TacticalPlan plan = stagePlan().orElseThrow();

        assertThat(plan.target()).isEqualTo(stagingPosition);
        assertThat(originsOf(plan)).containsExactlyInAnyOrder(launchPosition, second);
    }

    // A fortress (four dreadnoughts and two PDS) is out of reach of the fleet however long it waits, so the neighbour's
    // home is not worth staging for. A weak system of the same neighbour, a destroyer over a planet the seat wants,
    // is: the fleet moves to a system beside it, one step from where it now stands.
    @Test
    void stagesTowardsAWeakSystemRatherThanAFortress() {
        defendTheTargetWith(4, 0);
        Tile fortress = board.test.game.getTileByPosition(TARGET);
        board.test.units(fortress, "quinarra", board.sardakk, UnitType.Pds, 2);
        String beside = neighbours(launchPosition).stream()
                .filter(position -> free(position)
                        && !stagingPosition.equals(position)
                        && !"000".equals(position)
                        && !TARGET.equals(position)
                        && !neighbours(TARGET).contains(position))
                .findFirst()
                .orElseThrow();
        board.test.place("50", beside);
        String weak = neighbours(beside).stream()
                .filter(position -> free(position)
                        && !neighbours(launchPosition).contains(position)
                        && !neighbours(stagingPosition).contains(position)
                        && !TARGET.equals(position)
                        && !neighbours(TARGET).contains(position))
                .findFirst()
                .orElseThrow();
        Tile lodor = board.test.place("26", weak);
        board.sardakk.addPlanet("lodor");
        board.test.units(lodor, "space", board.sardakk, UnitType.Destroyer, 1);

        List<TacticalPlan> plans = stagePlans();

        assertThat(plans).isNotEmpty();
        assertThat(plans).noneMatch(plan -> plan.target().equals(stagingPosition));
        assertThat(plans).allMatch(plan -> neighbours(weak).contains(plan.target()));
        assertThat(plans).allMatch(plan -> originsOf(plan).equals(Set.of(launchPosition)));
    }

    // Two fleets that could each stage alone would together put four ships into the staging system, one more than the
    // seat's fleet supply of three allows: the smaller contribution stays behind.
    @Test
    void gatheringNeverExceedsFleetSupply() {
        secondLaunch(1);
        board.test.nekro.setFleetCC(5);
        assertThat(shipsMoved(stagePlan().orElseThrow())).isEqualTo(4);

        board.test.nekro.setFleetCC(3);

        List<TacticalPlan> plans = stagePlans();
        assertThat(plans).isNotEmpty();
        assertThat(plans).allMatch(plan -> shipsMoved(plan) <= 3);
    }
}
