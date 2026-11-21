package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Intimidate Council wants ships in two systems next to Mecatol Rex. Nekro already holds one (a destroyer in 102), so
// the build at home (301) should add a presence ship only when no ship on the board can take the second one, and pick
// a ship that can actually get there from the dock. 301 reaches 101 in two moves through 201.
class ProductionPresenceTest extends BaseTi4Test {

    private static final String MISSING = "101";
    private static final String COUNTED = "102";
    private static final String BETWEEN = "201";

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(4);
        home = test.nekroHome();
        test.nekro.setFleetCC(7);
        test.nekro.setTg(12);
        test.place("18", "000");
        Tile counted = test.place("48", COUNTED);
        test.units(counted, "space", test.nekro, UnitType.Destroyer, 1);
        test.game.getRevealedPublicObjectives().put("intimidate", 1);
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(test.game, test.nekro, home);
    }

    private static List<BuildOrder> scoringShips(BuildPlan plan) {
        return plan.orders().stream().filter(BuildOrder::scoring).toList();
    }

    private PresenceReach reach(Tile dock) {
        return PresenceReach.of(test.game, test.nekro, dock, new DefenseOutlook(test.game, test.nekro));
    }

    private void openRoute() {
        test.place("47", BETWEEN);
        test.place("46", MISSING);
    }

    // A destroyer one system from 101 takes it next round, so nothing is built for the objective.
    @Test
    void buildsNoPresenceShipWhenAShipOnTheBoardReachesTheMissingSystem() {
        openRoute();
        test.units(test.game.getTileByPosition(BETWEEN), "space", test.nekro, UnitType.Destroyer, 1);

        assertThat(reach(home).openings()).isEmpty();
        assertThat(scoringShips(plan())).isEmpty();
    }

    // The destroyer in 102 is next to 101, but moving it gives up 102, so one ship is built for 101. The old rule
    // always built two destroyer pawns.
    @Test
    void aShipHoldingTheOnlyCountedSystemDoesNotCoverAnother() {
        openRoute();

        assertThat(reach(home).openings())
                .extracting(PresenceReach.Opening::position)
                .containsExactly(MISSING);
        assertThat(scoringShips(plan())).hasSize(1);
    }

    // Without 201 on the board no ship built at 301 can get to 101 next round.
    @Test
    void buildsNoPresenceShipForASystemNoShipCanReachFromTheDock() {
        test.place("46", MISSING);

        assertThat(scoringShips(plan())).isEmpty();
    }

    // A carrier at home moves one system. Gravity Drive takes it the two systems to 101, so it covers the objective
    // and no pawn is built.
    @Test
    void gravityDriveLetsAMoveOneShipReachTheSystem() {
        openRoute();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        int withoutDrive = scoringShips(plan()).size();

        test.nekro.addTech("gd");

        assertThat(withoutDrive).isEqualTo(1);
        assertThat(scoringShips(plan())).isEmpty();
    }

    // A lone Sol carrier sits in 101. A destroyer would probably lose that fight; with cash to spare the planner builds
    // a ship that wins it.
    @Test
    void contestsAnEnemyHeldSystemWithAWarshipNotADestroyer() {
        openRoute();
        test.units(test.game.getTileByPosition(MISSING), "space", test.sol, UnitType.Carrier, 1);
        test.nekro.addTech("gd");
        test.nekro.setTg(20);

        List<BuildOrder> scoring = scoringShips(plan());

        assertThat(scoring).hasSize(1);
        assertThat(scoring.getFirst().type()).isIn(UnitType.Dreadnought, UnitType.Flagship);
    }

    @Test
    void shortCashPicksTheCheapestShipThatReaches() {
        openRoute();
        test.nekro.exhaustPlanet("mordaiii");
        test.nekro.setTg(2);

        List<BuildOrder> scoring = scoringShips(plan());

        assertThat(scoring).extracting(BuildOrder::type).containsExactly(UnitType.Destroyer);
    }

    // Any ship that gets to an empty 101 takes it, so even with cash to spare the presence ship is the cheapest one
    // that
    // reaches. The spare cash goes to the surplus ships instead.
    @Test
    void plentyOfCashStillSendsTheCheapestShipToAnEmptySystem() {
        openRoute();
        test.nekro.setTg(30);

        List<BuildOrder> scoring = scoringShips(plan());

        assertThat(scoring).extracting(BuildOrder::type).containsExactly(UnitType.Destroyer);
    }

    // A fleet pool of one leaves home a single slot. The destroyer planned for 101 takes that slot first. With Gravity
    // Drive the flagship also reaches 101, so it takes the slot and the presence job over;
    // without it the flagship could not get there and the destroyer keeps the slot.
    @Test
    void theFlagshipTakesTheLastSlotAndThePresenceJobWhenItReachesTheSystem() {
        openRoute();
        test.nekro.setFleetCC(1);
        test.nekro.setTg(30);
        List<BuildOrder> withoutDrive = scoringShips(plan());

        test.nekro.addTech("gd");
        BuildPlan withDrive = plan();

        assertThat(withoutDrive).extracting(BuildOrder::type).containsExactly(UnitType.Destroyer);
        assertThat(withDrive.units(UnitType.Destroyer)).isZero();
        assertThat(scoringShips(withDrive)).extracting(BuildOrder::type).containsExactly(UnitType.Flagship);
    }

    // A dock in 101 itself: whatever it builds there already counts, which the tactical planner credits through the
    // built units, so no presence opening is recorded for the dock system.
    @Test
    void theDockSystemIsLeftToBuiltGain() {
        test.place("47", BETWEEN);
        Tile dock = test.place("25", MISSING);
        test.nekro.addPlanet("quann");
        test.units(dock, "quann", test.nekro, UnitType.Spacedock, 1);

        assertThat(reach(dock).openings()).isEmpty();
        assertThat(scoringShips(ProductionPlanner.plan(test.game, test.nekro, dock)))
                .isEmpty();
    }
}
