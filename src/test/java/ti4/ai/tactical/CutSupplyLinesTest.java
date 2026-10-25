package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.secrets.SecretValue;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Cut Supply Lines needs one ship in a system that holds another player's space dock. A dock system with a PDS used to
// be off limits; now the seat goes in when a ship is likely to survive the space cannon dice, and counts the point at
// that chance, less when the owner of the dock can still send a fleet back.
class CutSupplyLinesTest extends BaseTi4Test {

    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    private AiTestGame test;
    private Tile home;
    private Tile dock;
    private String dockPosition;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        home = test.nekroHome();
        test.nekro.setTacticalCC(3);
        dockPosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        dock = test.place("19", dockPosition);
        test.sol.addPlanet("wellon");
        test.units(dock, "wellon", test.sol, UnitType.Spacedock, 1);
    }

    private void pdsCoversTheDock() {
        test.units(dock, "wellon", test.sol, UnitType.Pds, 1);
    }

    private Optional<TacticalPlan> positioning() {
        return TacticalPlanner.position(new TacticalPlanner.Context(test.game, test.nekro), dock);
    }

    private void destroyersAtHome(int count) {
        test.units(home, "space", test.nekro, UnitType.Destroyer, count);
    }

    private void solFleetBesideTheDock() {
        String beside = MapPositionMapper.getAdjacentTilePositions(dockPosition).stream()
                .filter(position -> !"x".equals(position)
                        && !AiTestGame.HOME.equals(position)
                        && test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
        Tile fleet = test.place("26", beside);
        test.units(fleet, "space", test.sol, UnitType.Cruiser, 1);
    }

    @Test
    void entersAnEnemyDockSystemTheSpaceCannonCovers() {
        test.nekro.setSecret("csl");
        destroyersAtHome(2);
        pdsCoversTheDock();

        TacticalPlan plan = positioning().orElseThrow();

        assertThat(plan.moves()).allMatch(move -> move.type() == UnitType.Destroyer);
    }

    @Test
    void leavesTheCoveredDockSystemAloneWithoutTheSecret() {
        destroyersAtHome(2);
        pdsCoversTheDock();

        assertThat(positioning()).isEmpty();
    }

    @Test
    void staysOutWhileTheDockOwnerHasShipsInTheSystem() {
        test.nekro.setSecret("csl");
        destroyersAtHome(2);
        pdsCoversTheDock();
        test.units(dock, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(positioning()).isEmpty();
    }

    @Test
    void scoresTheCoveredDockBelowTheOpenOneByTheChanceTheCannonKillsTheShip() {
        test.nekro.setSecret("csl");
        destroyersAtHome(1);
        double open = positioning().orElseThrow().score();
        pdsCoversTheDock();
        double covered = positioning().orElseThrow().score();

        assertThat(open - covered).isGreaterThan(VP * 0.4);
    }

    // One PDS die hits on a 6: it kills a lone ship half the time but cannot kill two. A second die can.
    @Test
    void countsTheChanceAnArrivingShipSurvivesTheDice() {
        pdsCoversTheDock();
        List<TacticalPlan.UnitMove> one =
                List.of(new TacticalPlan.UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 1));
        List<TacticalPlan.UnitMove> two =
                List.of(new TacticalPlan.UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 2));
        double oneDie = SupplyLines.survival(test.game, test.nekro, dock, one);
        double oneDieTwoShips = SupplyLines.survival(test.game, test.nekro, dock, two);
        pdsCoversTheDock();

        assertThat(oneDie).isCloseTo(0.5, within(0.001));
        assertThat(oneDieTwoShips).isEqualTo(1.0);
        assertThat(SupplyLines.survival(test.game, test.nekro, dock, one)).isCloseTo(0.25, within(0.001));
        assertThat(SupplyLines.survival(test.game, test.nekro, dock, two)).isCloseTo(0.75, within(0.001));
    }

    @Test
    void valuesTheCoveredDockHigherOnceItsOwnerHasPassed() {
        test.nekro.setSecret("csl");
        destroyersAtHome(1);
        pdsCoversTheDock();
        solFleetBesideTheDock();
        double owned = positioning().orElseThrow().score();

        test.sol.setPassed(true);
        double passed = positioning().orElseThrow().score();

        assertThat(passed - owned).isCloseTo(VP * 0.5 * 0.5, within(0.01));
    }

    @Test
    void holdsOnlyHalfWhenTheOwnerHasShipsWithinReachAndHasNotPassed() {
        solFleetBesideTheDock();

        assertThat(SupplyLines.hold(test.game, test.nekro, dock)).isEqualTo(0.5);
        test.sol.setPassed(true);
        assertThat(SupplyLines.hold(test.game, test.nekro, dock)).isEqualTo(1.0);
    }

    // The ship parked in the dock system is what holds the point: moving it away loses the whole victory point, so the
    // planner already treats it as a cost without a separate hold rule.
    @Test
    void movingTheParkedShipAwayCostsTheWholePoint() {
        test.nekro.setSecret("csl");
        test.units(dock, "space", test.nekro, UnitType.Destroyer, 1);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint away = value.before()
                .after(AiTestGame.HOME, List.of(new Footprint.Move(dockPosition, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(away)).isEqualTo(-VP);
    }

    @Test
    void keepsTheSecretAboveAnOrdinaryProgresslessOneWhileADockIsInReach() {
        test.nekro.setSecret("csl");
        destroyersAtHome(1);
        test.nekro.setSecret("dtd");

        for (int round = 1; round <= 4; round++) {
            test.game.setRound(round);
            assertThat(SupplyLines.targetInReach(test.game, test.nekro)).isTrue();
            assertThat(SecretValue.keepValue(test.game, test.nekro, "csl"))
                    .isGreaterThan(SecretValue.keepValue(test.game, test.nekro, "dtd"));
        }
    }

    @Test
    void keepsTheSecretLowerWhenNoDockIsInReach() {
        test.nekro.setSecret("csl");
        test.game.setRound(2);
        destroyersAtHome(1);
        double inReach = SecretValue.keepValue(test.game, test.nekro, "csl");

        test.units(dock, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(SupplyLines.targetInReach(test.game, test.nekro)).isFalse();
        assertThat(SecretValue.keepValue(test.game, test.nekro, "csl")).isLessThan(inReach);
    }
}
