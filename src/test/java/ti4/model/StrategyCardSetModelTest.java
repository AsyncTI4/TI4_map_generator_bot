package ti4.model;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StrategyCardSetModelTest extends BaseTi4Test {
    @Test
    public void testSCSetModels() {
        for (StrategyCardSetModel scSetModel : Mapper.getStrategyCardSets().values()) {
            assertTrue(scSetModel.isValid(), scSetModel.getAlias() + "'s data is invalid");
            assertTrue(validateSCIDs(scSetModel), scSetModel.getAlias() + " [" + scSetModel.getName() + "]: Invalid SC IDs");
            assertTrue(validateNoDuplicateInitiatives(scSetModel), scSetModel.getAlias() + " [" + scSetModel.getName() + "]: Duplicate Initiative Values");
        }
    }

    private static boolean validateSCIDs(StrategyCardSetModel scSetModel) {
        if (Mapper.getStrategyCards().keySet().containsAll(scSetModel.getScIDs()))
            return true;
        System.out.println("SCSet **" + scSetModel.getName() + "** failed validation due to invalid SC IDs: `" + scSetModel.getScIDs() + "`");
        return false;
    }

    private static boolean validateNoDuplicateInitiatives(StrategyCardSetModel scSetModel) {
        return scSetModel.getScIDs().stream()
            .map(Mapper::getStrategyCard)
            .map(StrategyCardModel::getInitiative)
            .distinct()
            .count() == scSetModel.getScIDs().stream()
                .count();
    }
}
