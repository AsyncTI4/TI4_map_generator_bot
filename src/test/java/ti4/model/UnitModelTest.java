package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;

public class UnitModelTest {
    
    @BeforeAll
    public static void init() {
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }

    @Test
    public void testUnitModels() {
        for (UnitModel unitModel : Mapper.getUnits().values()) {
            assertTrue(validateBaseType(unitModel));
            assertTrue(validateRequiredTechId(unitModel));
            assertTrue(validateUpgradesToUnitId(unitModel));
            assertTrue(validateUpgradesFromUnitId(unitModel));
            assertTrue(validateHomebrewReplacesID(unitModel));
        }
    }
    
    private static boolean validateBaseType(UnitModel unitModel) {
        if (Mapper.isValidUnit(unitModel.getBaseType())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId() + "** failed validation due to invalid BaseType: `" + unitModel.getBaseType() + "`");
        return false;
    }
    
    private static boolean validateUpgradesFromUnitId(UnitModel unitModel) {
        if (unitModel.getUpgradesFromUnitId().isEmpty()) return true;
        if (Mapper.isValidUnit(unitModel.getUpgradesFromUnitId().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId() + "** failed validation due to invalid UpgradesFromUnitId ID: `" + unitModel.getUpgradesFromUnitId().get() + "`");
        return false;
    }
    
    private static boolean validateUpgradesToUnitId(UnitModel unitModel) {
        if (unitModel.getUpgradesToUnitId().isEmpty()) return true;
        if (Mapper.isValidUnit(unitModel.getUpgradesToUnitId().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId() + "** failed validation due to invalid UpgradesToUnitId ID: `" + unitModel.getUpgradesToUnitId().get() + "`");
        return false;
    }

    private static boolean validateRequiredTechId(UnitModel unitModel) {
        if (unitModel.getRequiredTechId().isEmpty()) return true;
        if (Mapper.isValidTech(unitModel.getRequiredTechId().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId() + "** failed validation due to invalid RequiredTechId ID: `" + unitModel.getRequiredTechId().get() + "`");
        return false;
    }

    private static boolean validateHomebrewReplacesID(UnitModel unitModel) {
        if (unitModel.getHomebrewReplacesID().isEmpty()) return true;
        if (Mapper.isValidUnit(unitModel.getHomebrewReplacesID().get())) return true;
        System.out.println("[TEST FAILURE] Unit **" + unitModel.getId() + "** failed validation due to invalid HomebrewReplacesID: `" + unitModel.getHomebrewReplacesID().get() + "`");
        return false;
    }
}
