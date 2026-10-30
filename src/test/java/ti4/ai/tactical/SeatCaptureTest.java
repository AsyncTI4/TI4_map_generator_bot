package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Occupy the Seat of the Empire against a rival who holds Mecatol Rex: a strike that takes the planet and leaves three
// ships is worth the point, the ships count as the strike leaves them, a stack that fills the fleet supply can still
// take delivery of ground forces, and staging toward the planet is worth the point too.
class SeatCaptureTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    private DefenseScenario board;
    private Tile rex;
    private String launchPosition;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        rex = board.test.place("18", MECATOL);
        sardakk().addPlanet("mr");
        board.test.units(rex, "mr", sardakk(), UnitType.Infantry, 1);
    }

    private Player nekro() {
        return board.test.nekro;
    }

    private Player sardakk() {
        return board.sardakk;
    }

    private String nextTo(String position, String... excluded) {
        List<String> skip = List.of(excluded);
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !skip.contains(candidate)
                        && board.test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
    }

    private void raidersOneMoveFromMecatol(int ships) {
        launchPosition = nextTo(MECATOL);
        Tile launch = board.test.place("47", launchPosition);
        board.test.units(launch, "space", nekro(), UnitType.Carrier, 1);
        if (ships > 1) board.test.units(launch, "space", nekro(), UnitType.Cruiser, ships - 1);
        board.test.units(launch, "space", nekro(), UnitType.Infantry, 3);
    }

    private StrikeValue breakdown() {
        return TacticalPlanner.explainAttack(board.test.game, nekro(), MECATOL).orElseThrow();
    }

    @Test
    void aCaptureThatLeavesThreeShipsIsWorthTheSeat() {
        raidersOneMoveFromMecatol(3);
        double without = breakdown().ownObjective();
        nekro().setSecret("ose");

        double with = breakdown().ownObjective();

        assertThat(without).isZero();
        assertThat(with).isGreaterThan(VP / 2);
        assertThat(with).isLessThanOrEqualTo(VP);
    }

    @Test
    void theCaptureIsAnAttackPlanThatLandsOnMecatol() {
        raidersOneMoveFromMecatol(3);
        nekro().setSecret("ose");

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(board.test.game, nekro(), MECATOL);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.ATTACK);
        assertThat(plan.get().landings()).containsKey("mr");
    }

    @Test
    void shipsLostInTheStrikeDoNotCountForTheSeat() {
        raidersOneMoveFromMecatol(3);
        nekro().setSecret("ose");
        TacticalPlanner.Context context = new TacticalPlanner.Context(board.test.game, nekro());
        List<UnitMove> moves = List.of(
                new UnitMove(launchPosition, "space", UnitType.Carrier, 1),
                new UnitMove(launchPosition, "space", UnitType.Cruiser, 2));
        StrikeEstimate allSurvive = estimate(Map.of(UnitType.Carrier, 1, UnitType.Cruiser, 2));
        StrikeEstimate oneLost = estimate(Map.of(UnitType.Carrier, 1, UnitType.Cruiser, 1));

        assertThat(context.seatAdjustment(rex, moves, allSurvive)).isZero();
        assertThat(context.seatAdjustment(rex, moves, oneLost)).isCloseTo(VP * 0.6 * 2 / 3 - VP, within(1e-9));
    }

    private StrikeEstimate estimate(Map<UnitType, Integer> survivors) {
        Map<String, Integer> landed = Map.of("mr", 2);
        return new StrikeEstimate(
                1, false, false, true, landed, Map.of("mr", 1.0), landed, 0, 0, 0, 0, 0, 0, survivors);
    }

    @Test
    void aStackThatFillsTheFleetSupplyStillTakesDeliveryOfGroundForces() {
        nekro().setSecret("ose");
        nekro().setFleetCC(3);
        board.test.units(rex, "space", nekro(), UnitType.Destroyer, 2);
        board.test.units(rex, "space", nekro(), UnitType.Cruiser, 1);
        Tile launch = board.test.place("47", nextTo(MECATOL));
        board.test.units(launch, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(launch, "space", nekro(), UnitType.Infantry, 3);

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(board.test.game, nekro(), MECATOL);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.ATTACK);
        assertThat(plan.get().landings()).containsKey("mr");
    }

    @Test
    void aFleetThatCouldRetakeItRaisesTheCounterstrikeMoreWhileTheSeatIsHeld() {
        raidersOneMoveFromMecatol(3);
        Tile hunters = board.test.place("49", nextTo(MECATOL, launchPosition));
        board.test.units(hunters, "space", sardakk(), UnitType.Dreadnought, 3);
        board.test.units(hunters, "space", sardakk(), UnitType.Carrier, 1);
        board.test.units(hunters, "space", sardakk(), UnitType.Infantry, 4);
        sardakk().setFleetCC(6);
        double without = breakdown().counter();
        nekro().setSecret("ose");

        double with = breakdown().counter();

        assertThat(with).isGreaterThan(without);
    }

    @Test
    void stagingTowardMecatolIsWorthMoreToAHolder() {
        String staging = nextTo(MECATOL);
        board.test.place("48", staging);
        String launchSpot = MapPositionMapper.getAdjacentTilePositions(staging).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !MECATOL.equals(candidate)
                        && !MapPositionMapper.getAdjacentTilePositions(MECATOL).contains(candidate)
                        && board.test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
        Tile launch = board.test.place("47", launchSpot);
        board.test.units(launch, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(launch, "space", nekro(), UnitType.Cruiser, 2);
        board.test.units(launch, "space", nekro(), UnitType.Infantry, 3);
        double without = stageScore(staging);
        nekro().setSecret("ose");

        double with = stageScore(staging);

        assertThat(with).isGreaterThan(without);
    }

    private double stageScore(String staging) {
        return TacticalPlanner.ranked(board.test.game, nekro()).stream()
                .filter(plan -> plan.kind() == Kind.STAGE && plan.target().equals(staging))
                .mapToDouble(TacticalPlan::score)
                .max()
                .orElse(0);
    }

    @Test
    void feasibilityIsFullOnceTheSeatControlsMecatol() {
        nekro().addPlanet("mr");
        sardakk().removePlanet("mr");

        assertThat(SeatStrike.feasibility(board.test.game, nekro())).isEqualTo(1.0);
    }

    @Test
    void feasibilityIsLowWithoutAFleetInReachOfARivalHeldMecatol() {
        double hopeless = SeatStrike.feasibility(board.test.game, nekro());
        raidersOneMoveFromMecatol(3);
        double reachable = SeatStrike.feasibility(board.test.game, nekro());

        assertThat(hopeless).isLessThan(0.5);
        assertThat(reachable).isGreaterThan(hopeless);
    }
}
