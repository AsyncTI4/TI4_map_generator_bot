package ti4.model;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExploreModelTest extends BaseTi4Test {
    @Test
    public void testExplores() {
        for (ExploreModel model : Mapper.getExplores().values()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validateAttachmentID(model), model.getAlias() + ": invalid AttachmentID: " + model.getAttachmentId().orElse(""));
        }
    }

    private boolean validateAttachmentID(ExploreModel model) {
        if (model.getAttachmentId().isEmpty()) return true;
        if (Mapper.isValidAttachment(model.getAttachmentId().get())) return true;
        if (Mapper.isValidToken(model.getAttachmentId().get())) return true;
        System.out.println("Explore **" + model.getAlias() + "** failed validation due to invalid AttachmentID: `" + model.getAttachmentId().get() + "`");
        return false;
    }
}
