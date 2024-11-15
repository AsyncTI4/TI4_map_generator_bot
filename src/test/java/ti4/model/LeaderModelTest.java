package ti4.model;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LeaderModelTest extends BaseTi4Test {
    @Test
    public void testLeaders() {
        for (LeaderModel model : Mapper.getLeaders().values()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validateFaction(model), model.getAlias() + ": invalid FactionID");
            assertTrue(validateHomebrewReplacesID(model), model.getAlias() + ": invalid HomebrewReplacesID");
        }
    }

    private boolean validateFaction(LeaderModel model) {
        if (model.getFaction().isEmpty())
            return true;
        if (Mapper.isValidFaction(model.getFaction()) || "keleres".equals(model.getFaction()) || "fogalliance".equals(model.getFaction()) || "generic".equals(model.getFaction()))
            return true;
        System.out.println("Tech **" + model.getAlias() + "** failed validation due to invalid FactionID: `" + model.getFaction() + "`");
        return false;
    }

    private boolean validateHomebrewReplacesID(LeaderModel techModel) {
        if (techModel.getHomebrewReplacesID().isEmpty())
            return true;
        if (Mapper.isValidLeader(techModel.getHomebrewReplacesID().get()))
            return true;
        System.out.println(
            "Tech **" + techModel.getAlias() + "** failed validation due to invalid HomebrewReplacesID ID: `" + techModel.getHomebrewReplacesID().get() + "`");
        return false;
    }
}
