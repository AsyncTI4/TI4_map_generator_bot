package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.message.BotLogger;

public class TechnologyModelTest {

    @BeforeAll
    public static void init() {
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }

    @Test
    public void testTechnologies() {
        for (TechnologyModel techModel : Mapper.getTechs().values()) {
            assertTrue(techModel.isValid());
            assertTrue(validateFaction(techModel), techModel.getAlias() + ": invalid FactionID");
            assertTrue(validateBaseUpgrade(techModel));
            assertTrue(validateHomebrewReplacesID(techModel));
        }
    }

    private boolean validateFaction(TechnologyModel techModel) {
        if (techModel.getFaction().isEmpty()) return true;
        if (Mapper.isFaction(techModel.getFaction().get()) || "keleres".equals(techModel.getFaction().get())) return true;
        BotLogger.log("Tech **" + techModel.getAlias() + "** failed validation due to invalid FactionID: `" + techModel.getFaction().get() + "`");
        return false;
    }

    private boolean validateBaseUpgrade(TechnologyModel techModel) {
        if (techModel.getBaseUpgrade().isEmpty()) return true;
        if (Mapper.isValidTech(techModel.getBaseUpgrade().get())) return true;
        BotLogger.log("Tech **" + techModel.getAlias() + "** failed validation due to invalid BaseUpgrade ID: `" + techModel.getBaseUpgrade().get() + "`");
        return false;
    }

    private boolean validateHomebrewReplacesID(TechnologyModel techModel) {
        if (techModel.getHomebrewReplacesID().isEmpty()) return true;
        if (Mapper.isValidTech(techModel.getHomebrewReplacesID().get())) return true;
        BotLogger.log("Tech **" + techModel.getAlias() + "** failed validation due to invalid HomebrewReplacesID ID: `" + techModel.getHomebrewReplacesID().get() + "`");
        return false;
    }
}
