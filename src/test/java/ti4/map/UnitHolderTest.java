package ti4.map;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import ti4.helpers.Units;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class UnitHolderTest {

    public int sum(List<Integer> vals) {
        return vals.stream().collect(Collectors.summingInt(i -> i));
    }

    @Test
    public void testRemovingUnitsInExcess() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 2);

        List<Integer> actuallyRemoved = unitHolder.removeUnit(unitKey, 5);

        assertThat(sum(actuallyRemoved)).isEqualTo(2);
    }

    @Test
    public void testRemovingExactNumberOfUnits() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 2);

        List<Integer> actuallyRemoved = unitHolder.removeUnit(unitKey, 2);

        assertThat(sum(actuallyRemoved)).isEqualTo(2);
    }

    @Test
    public void testRemovingSomeOfTheUnits() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 5);

        List<Integer> actuallyRemoved = unitHolder.removeUnit(unitKey, 2);

        assertThat(sum(actuallyRemoved)).isEqualTo(2);
    }

}