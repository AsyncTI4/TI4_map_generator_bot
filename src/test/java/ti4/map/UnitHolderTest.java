package ti4.map;

import java.awt.*;

import org.junit.jupiter.api.Test;
import ti4.helpers.Units;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class UnitHolderTest {

    @Test
    public void testRemovingUnitsInExcess() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 2);

        int actuallyRemoved = unitHolder.removeUnit(unitKey, 5);

        assertThat(actuallyRemoved).isEqualTo(2);
    }

    @Test
    public void testRemovingExactNumberOfUnits() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 2);

        int actuallyRemoved = unitHolder.removeUnit(unitKey, 2);

        assertThat(actuallyRemoved).isEqualTo(2);
    }

    @Test
    public void testRemovingSomeOfTheUnits() {
        Units.UnitKey unitKey = new Units.UnitKey(Units.UnitType.Destroyer, "red");

        UnitHolder unitHolder = new Space("whatever", new Point(0, 0));
        unitHolder.addUnit(unitKey, 5);

        int actuallyRemoved = unitHolder.removeUnit(unitKey, 2);

        assertThat(actuallyRemoved).isEqualTo(2);
    }

}