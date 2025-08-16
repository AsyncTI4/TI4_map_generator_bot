package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class StrategyCardModelTest extends BaseTi4Test {
    @Test
    void testStrategyCardModels() {
        for (StrategyCardModel scModel : Mapper.getStrategyCards().values()) {
            assertTrue(scModel.isValid(), scModel.getAlias() + "'s data is invalid");
            assertTrue(
                    validateBotSCAutomationID(scModel),
                    scModel.getAlias() + " [" + scModel.getName() + "]: Invalid BotSCAutomationID");
        }
    }

    private static boolean validateBotSCAutomationID(StrategyCardModel scModel) {
        if (Mapper.getStrategyCards().containsKey(scModel.getBotSCAutomationID())) return true;
        System.out.println("SCSet **" + scModel.getName() + "** failed validation due to invalid BotSCAutomationID: `"
                + scModel.getBotSCAutomationID() + "`");
        return false;
    }
}
