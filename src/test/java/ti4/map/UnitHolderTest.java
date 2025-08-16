package ti4.map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.awt.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units;

class UnitHolderTest {

    private int sum(List<Integer> vals) {
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
}
