package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.GroundDrops.Drop;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// What 2 free infantry (and a deployed mech) are worth on each planet the seat controls: holding it against a landing,
// filling the carriers waiting to launch one, and secret objectives that count ground forces.
class GroundDropsTest extends BaseTi4Test {

    private static final String PLANET = "mordaiii";

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        board.test.nekro.setStrategicCC(3);
    }

    private Drop drop() {
        return GroundDrops.best(board.test.game, board.test.nekro, Optional.empty())
                .orElseThrow();
    }

    @Test
    void aQuietPlanetOnlyGetsTheFloorForTheInfantry() {
        Drop quiet = drop();

        assertThat(quiet.planet()).isEqualTo(PLANET);
        assertThat(quiet.infantry()).isEqualTo(0.6);
        assertThat(quiet.mech()).isZero();
    }

    @Test
    void aLandingInReachMakesTheDropWorthMore() {
        board.test.units(board.home, PLANET, board.test.nekro, UnitType.Infantry, 2);
        board.raidingFleet(2, 2, 4);

        assertThat(drop().infantry()).isGreaterThan(2.0);
    }

    @Test
    void aMechIsWorthMoreWhenTheInfantryAloneWouldNotHold() {
        board.test.units(board.home, PLANET, board.test.nekro, UnitType.Infantry, 2);
        board.raidingFleet(3, 3, 6);

        assertThat(drop().mech()).isGreaterThan(1.0);
    }

    @Test
    void emptyCarriersWaitingToLaunchWantTheInfantry() {
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 2);
        Drop loaded = drop();
        board.test.units(board.home, PLANET, board.test.nekro, UnitType.Infantry, 9);

        assertThat(loaded.infantry()).isGreaterThan(1.0);
        assertThat(drop().infantry()).isLessThan(loaded.infantry());
    }

    @Test
    void completingOccupyTheFringeIsWorthMostOfAPointAndBuildingTowardItASharePerDrop() {
        board.test.place(
                "26",
                board.stagingPosition.equals(board.otherNeighbourOfHome())
                        ? board.beyondStaging()
                        : board.otherNeighbourOfHome());
        board.test.nekro.addPlanet("lodor");
        board.test.nekro.setSecret("otf");
        Tile outpost = board.test.game.getTileFromPlanet("lodor");
        board.test.units(outpost, "lodor", board.test.nekro, UnitType.Infantry, 6);
        double short_ = GroundDrops.drops(board.test.game, board.test.nekro, Optional.empty()).stream()
                .filter(drop -> "lodor".equals(drop.planet()))
                .findFirst()
                .orElseThrow()
                .infantry();
        board.test.units(outpost, "lodor", board.test.nekro, UnitType.Infantry, 1);

        Drop completing = GroundDrops.drops(board.test.game, board.test.nekro, Optional.empty()).stream()
                .filter(drop -> "lodor".equals(drop.planet()))
                .findFirst()
                .orElseThrow();

        assertThat(short_).isGreaterThan(1.0).isLessThan(completing.infantry());
        assertThat(completing.infantry()).isGreaterThan(6.0);
    }

    @Test
    void aMechOnAPlanetWithoutOneAdvancesMechanizeTheMilitary() {
        board.test.nekro.setSecret("mtm");

        assertThat(drop().mech()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void nothingToDropWhenTheInfantryIsAllOnTheBoard() {
        board.test.nekro.setUnitCap("gf", 3);
        board.test.units(board.home, PLANET, board.test.nekro, UnitType.Infantry, 3);

        assertThat(GroundDrops.best(board.test.game, board.test.nekro, Optional.empty()))
                .isEmpty();
    }
}
