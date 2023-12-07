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

public class ExploreModelTest {

    @BeforeAll
    public static void init() {
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }

    @Test
    public void testExplores() {
        for (ExploreModel model : Mapper.getExplores().values()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            // assertTrue(validateAttachmentID(model), model.getAlias() + ": invalid AttachmentID: " + model.getAttachmentId().orElse(""));
        }
    }

    private boolean validateAttachmentID(ExploreModel model) {
        if (model.getAttachmentId().isEmpty()) return true;
        if (Mapper.isValidAttachment(model.getAttachmentId().get())) return true;
        BotLogger.log("Explore **" + model.getAlias() + "** failed validation due to invalid AttachmentID: `" + model.getAttachmentId().get() + "`");
        return false;
    }
}
