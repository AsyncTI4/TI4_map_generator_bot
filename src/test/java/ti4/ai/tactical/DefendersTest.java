package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class DefendersTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
    }

    @Test
    void readsTheSeatsShipsAndGroundForcesWithTheirDamage() {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        home.addUnitDamage("space", Units.getUnitKey(UnitType.Dreadnought, test.nekro.getColor()), 2);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);

        Defenders defenders = Defenders.of(test.nekro, home);

        assertThat(defenders.ships().count(UnitType.Dreadnought)).isEqualTo(3);
        assertThat(defenders.ships().damaged()).containsEntry(UnitType.Dreadnought, 2);
        assertThat(defenders.ships().count(UnitType.Fighter)).isEqualTo(2);
        assertThat(defenders.groundOn("mordaiii")).isEqualTo(3);
    }

    // Ships that leave are the damaged ones first, which is the less defensive reading of an unspecified departure.
    @Test
    void damagedShipsLeaveFirst() {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        home.addUnitDamage("space", Units.getUnitKey(UnitType.Dreadnought, test.nekro.getColor()), 2);

        Defenders after = Defenders.of(test.nekro, home)
                .minus(List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 1)));

        assertThat(after.ships().count(UnitType.Dreadnought)).isEqualTo(2);
        assertThat(after.ships().damaged()).containsEntry(UnitType.Dreadnought, 1);
    }

    @Test
    void movesFromPlanetsAndSpaceTakeUnitsOutOfTheRightHolder() {
        test.units(home, "space", test.nekro, UnitType.Infantry, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);

        Defenders after = Defenders.of(test.nekro, home)
                .minus(List.of(
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 2),
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Infantry, 1)));

        assertThat(after.groundOn("mordaiii")).isEqualTo(1);
        assertThat(after.groundInSpace().count(UnitType.Infantry)).isEqualTo(1);
    }

    @Test
    void keepingGroundForcesKeepsMechsFirst() {
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 1);

        Defenders.Stack kept =
                Defenders.of(test.nekro, home).withGround("mordaiii", 2).on("mordaiii");

        assertThat(kept.count(UnitType.Mech)).isEqualTo(1);
        assertThat(kept.count(UnitType.Infantry)).isEqualTo(1);
    }

    @Test
    void addsUnitsAndPricesTheFleet() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        Defenders defenders = Defenders.of(test.nekro, home);

        Defenders grown = defenders.plusShips(UnitType.Cruiser, 2, 0).plusGround("mordaiii", UnitType.Infantry, 2);

        assertThat(grown.shipCost(test.nekro)).isGreaterThan(defenders.shipCost(test.nekro));
        assertThat(grown.groundOn("mordaiii")).isEqualTo(2);
        assertThat(grown.key()).isNotEqualTo(defenders.key());
        assertThat(defenders.plusGround("mordaiii", UnitType.Infantry, 1).key())
                .isEqualTo(
                        defenders.plusGround("mordaiii", UnitType.Infantry, 1).key());
    }
}
