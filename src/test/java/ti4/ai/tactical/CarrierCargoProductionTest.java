package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Carriers are worth what they can carry: fighters and ground forces waiting at the dock, spare ground forces a carrier
// can pick up next door, and the cargo the dock can still build this turn, set against the capacity already at the
// dock. Two carriers sit in a system next to home, so the two expansion carriers are already out (Nekro may have four).
// The flagship sits at home: it is out of the build and its capacity (3) is at the dock.
class CarrierCargoProductionTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(4);
        home = test.nekroHome();
        test.nekro.setFleetCC(10);
        test.nekro.setTg(20);
        Tile away = test.place("47", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(away, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Flagship, 1);
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(test.game, test.nekro, home);
    }

    // A carrier and the flagship at home: seven slots for one infantry and the five units the dock can still add.
    private void homeCanCarryItsBuild() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
    }

    @Test
    void buildsMoreCarriersWhileGroundForcesWaitAtTheDock() {
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 12);

        assertThat(plan().units(UnitType.Carrier)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void buildsNoExtraCarrierWhenTheDockCanAlreadyCarryItsGroundForces() {
        homeCanCarryItsBuild();

        assertThat(plan().units(UnitType.Carrier)).isZero();
    }

    // The same home, plus eight infantry: more than the carrier and flagship can lift.
    @Test
    void groundForcesAtTheDockCountAsCargo() {
        homeCanCarryItsBuild();
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 8);

        assertThat(plan().units(UnitType.Carrier)).isPositive();
    }

    // Home is empty and the dock can still build more ground forces and fighters than a carrier holds. A carrier is
    // built, and the rest of the build fills it. With the flagship's three slots at home, the same build would leave a
    // new carrier half empty, so none is built.
    @Test
    void buildsACarrierForCargoStillToBeBuiltOnlyWhenItWouldBeFull() {
        assertThat(plan().units(UnitType.Carrier)).isZero();

        BoardView.space(home).removeAllUnits(test.nekro.getColor());
        BuildPlan plan = plan();

        int cargo = plan.units(UnitType.Infantry) + plan.units(UnitType.Mech) + plan.units(UnitType.Fighter);
        assertThat(plan.units(UnitType.Carrier)).isPositive();
        assertThat(cargo).isGreaterThanOrEqualTo(BoardView.capacity(test.nekro, UnitType.Carrier));
    }

    // Spare infantry on a planet next door, with no ship there to lift them, is cargo a new carrier can pick up.
    @Test
    void spareInfantryNextDoorCountsAsCargo() {
        homeCanCarryItsBuild();
        assertThat(plan().units(UnitType.Carrier)).isZero();

        String next = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(AiTestGame.neighbourOf(AiTestGame.HOME)))
                .findFirst()
                .orElseThrow();
        Tile lodor = test.place("26", next);
        test.nekro.addPlanet("lodor");
        test.units(lodor, "lodor", test.nekro, UnitType.Infantry, 8);

        assertThat(plan().units(UnitType.Carrier)).isPositive();
    }

    // The space dock holds three fighters on its own. Three fighters at home need no carrier; nine do.
    @Test
    void onlyFightersPastTheDockAllowanceCountAsCargo() {
        homeCanCarryItsBuild();
        test.units(home, "space", test.nekro, UnitType.Fighter, BoardView.DOCK_FIGHTER_ALLOWANCE);
        assertThat(plan().units(UnitType.Carrier)).isZero();

        test.units(home, "space", test.nekro, UnitType.Fighter, 6);

        assertThat(plan().units(UnitType.Carrier)).isPositive();
    }

    // One fleet slot left and a dozen infantry waiting: a carrier to move them beats a dreadnought.
    @Test
    void aLoadedCarrierBeatsADreadnoughtForTheLastSlot() {
        test.nekro.setFleetCC(6);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 4);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 12);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Carrier)).isEqualTo(1);
        assertThat(plan.units(UnitType.Dreadnought)).isZero();
    }
}
