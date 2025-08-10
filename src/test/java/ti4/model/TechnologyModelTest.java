package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

public class TechnologyModelTest extends BaseTi4Test {
    @Test
    public void testTechnologies() {
        for (TechnologyModel techModel : Mapper.getTechs().values()) {
            assertTrue(techModel.isValid(), techModel.getAlias() + ": invalid");
            assertTrue(validateFaction(techModel), techModel.getAlias() + ": invalid FactionID");
            assertTrue(validateBaseUpgrade(techModel), techModel.getAlias() + ": invalid BaseUpgrade");
            assertTrue(validateHomebrewReplacesID(techModel), techModel.getAlias() + ": invalid HomebrewReplacesID");
        }
    }

    private boolean validateFaction(TechnologyModel techModel) {
        if (techModel.getFaction().isEmpty()) return true;
        if (Mapper.isValidFaction(techModel.getFaction().get())
                || "keleres".equals(techModel.getFaction().get())) return true;
        System.out.println("Tech **" + techModel.getAlias() + "** failed validation due to invalid FactionID: `"
                + techModel.getFaction().get() + "`");
        return false;
    }

    private boolean validateBaseUpgrade(TechnologyModel techModel) {
        if (techModel.getBaseUpgrade().isEmpty()) return true;
        if (Mapper.isValidTech(techModel.getBaseUpgrade().get())) return true;
        System.out.println("Tech **" + techModel.getAlias() + "** failed validation due to invalid BaseUpgrade ID: `"
                + techModel.getBaseUpgrade().get() + "`");
        return false;
    }

    private boolean validateHomebrewReplacesID(TechnologyModel techModel) {
        if (techModel.getHomebrewReplacesID().isEmpty()) return true;
        if (Mapper.isValidTech(techModel.getHomebrewReplacesID().get())) return true;
        System.out.println(
                "Tech **" + techModel.getAlias() + "** failed validation due to invalid HomebrewReplacesID ID: `"
                        + techModel.getHomebrewReplacesID().get() + "`");
        return false;
    }
}
