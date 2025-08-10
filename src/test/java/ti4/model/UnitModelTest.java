package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

public class UnitModelTest extends BaseTi4Test {
    @Test
    public void testUnitModels() {
        for (UnitModel unitModel : Mapper.getUnits().values()) {
            assertTrue(
                    validateBaseType(unitModel),
                    unitModel.getAlias() + ": invalid BaseType: " + unitModel.getBaseType());
            assertTrue(
                    validateRequiredTechId(unitModel),
                    unitModel.getAlias() + ": invalid RequiredTechId: "
                            + unitModel.getRequiredTechId().orElse(null));
            assertTrue(
                    validateUpgradesToUnitId(unitModel),
                    unitModel.getAlias() + ": invalid UpgradesToUnitId: "
                            + unitModel.getUpgradesToUnitId().orElse(null));
            assertTrue(
                    validateUpgradesFromUnitId(unitModel),
                    unitModel.getAlias() + ": invalid UpgradesFromUnitId: "
                            + unitModel.getUpgradesFromUnitId().orElse(null));
            assertTrue(
                    validateHomebrewReplacesID(unitModel),
                    unitModel.getAlias() + ": invalid HomebrewReplacesID: "
                            + unitModel.getHomebrewReplacesID().orElse(null));
            assertTrue(
                    validateAsyncID(unitModel), unitModel.getAlias() + ": invalid asyncID: " + unitModel.getAsyncId());
        }
    }

    private static boolean validateBaseType(UnitModel unitModel) {
        if (Mapper.isValidUnit(unitModel.getBaseType())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId()
                + "** failed validation due to invalid BaseType: `" + unitModel.getBaseType() + "`");
        return false;
    }

    private static boolean validateUpgradesFromUnitId(UnitModel unitModel) {
        if (unitModel.getUpgradesFromUnitId().isEmpty()) return true;
        if (Mapper.isValidUnit(unitModel.getUpgradesFromUnitId().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId()
                + "** failed validation due to invalid UpgradesFromUnitId ID: `"
                + unitModel.getUpgradesFromUnitId().get() + "`");
        return false;
    }

    private static boolean validateUpgradesToUnitId(UnitModel unitModel) {
        if (unitModel.getUpgradesToUnitId().isEmpty()) return true;
        if (Mapper.isValidUnit(unitModel.getUpgradesToUnitId().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId()
                + "** failed validation due to invalid UpgradesToUnitId ID: `"
                + unitModel.getUpgradesToUnitId().get() + "`");
        return false;
    }

    private static boolean validateRequiredTechId(UnitModel unitModel) {
        if (unitModel.getRequiredTechId().isEmpty()) return true;
        if (Mapper.isValidTech(unitModel.getRequiredTechId().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId()
                + "** failed validation due to invalid RequiredTechId ID: `"
                + unitModel.getRequiredTechId().get() + "`");
        return false;
    }

    private static boolean validateHomebrewReplacesID(UnitModel unitModel) {
        if (unitModel.getHomebrewReplacesID().isEmpty()) return true;
        if (Mapper.isValidUnit(unitModel.getHomebrewReplacesID().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId()
                + "** failed validation due to invalid HomebrewReplacesID: `"
                + unitModel.getHomebrewReplacesID().get() + "`");
        return false;
    }

    private static boolean validateAsyncID(UnitModel unitModel) {
        if (Stream.of(UnitType.values()).anyMatch(type -> type.value.equalsIgnoreCase(unitModel.getAsyncId())))
            return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId()
                + "** failed validation due to invalid AsyncID: `" + unitModel.getAsyncId() + "`");
        return false;
    }
}
