package ti4.game;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.awt.Point;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units;

class UnitHolderTest {

    private static int sum(List<Integer> vals) {
        return vals.stream().mapToInt(i -> i).sum();
    }

    @Test
    void testRemovingUnitsInExcess() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 2);

        List<Integer> actuallyRemoved = unitHolder.removeUnit(unitKey, 5);

        assertThat(sum(actuallyRemoved)).isEqualTo(2);
    }

    @Test
    void testRemovingExactNumberOfUnits() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 2);

        List<Integer> actuallyRemoved = unitHolder.removeUnit(unitKey, 2);

        assertThat(sum(actuallyRemoved)).isEqualTo(2);
    }

    @Test
    void testRemovingSomeOfTheUnits() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 5);

        List<Integer> actuallyRemoved = unitHolder.removeUnit(unitKey, 2);

        assertThat(sum(actuallyRemoved)).isEqualTo(2);
    }

    @Test
    void testUnitCountIncludesGalvanizedStates() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Dreadnought, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnitsWithStates(unitKey, List.of(1, 2, 3, 4));

        assertThat(unitHolder.getUnitCount(unitKey)).isEqualTo(10);
        assertThat(unitHolder.getUnitCount()).isEqualTo(10);
    }

    // A huge count (1314000146 infantry in pbd26171) once ran the renderer and the unit emoji list out of memory.
    @Test
    void infantryInOneHolderStopAt999() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Infantry, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 5);
        unitHolder.addUnit(unitKey, Integer.MAX_VALUE);

        assertThat(unitHolder.getUnitCount(unitKey)).isEqualTo(SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);
    }

    @Test
    void plasticUnitsInOneHolderStopAt99() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Warsun, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 1000);

        assertThat(unitHolder.getUnitCount(unitKey)).isEqualTo(SanityLimits.PLASTIC_UNITS);
    }

    @Test
    void statedAddsShareOneHolderLimit() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Dreadnought, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnitsWithStates(unitKey, List.of(Integer.MAX_VALUE, 3, 0, 0));

        assertThat(unitHolder.getUnitStates(unitKey).subList(0, 2)).containsExactly(SanityLimits.PLASTIC_UNITS, 0);
    }

    @Test
    void cappingStatesKeepsTheEarliestStates() {
        List<Integer> capped =
                UnitHolder.capStates(List.of(990, 20, 5, 0), SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);

        assertThat(capped).containsExactly(990, 9, 0, 0);
    }
}
