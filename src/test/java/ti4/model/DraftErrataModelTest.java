package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.draft.DraftItem;
import ti4.image.Mapper;

class DraftErrataModelTest extends ModelTest<DraftErrataModel> {

    @Override
    public void loadData() {
        type = "DraftErrataModel";
        models = Mapper.getFrankenErrata();
    }

    @Test
    void testDraftErrata() {
        for (DraftErrataModel model : getModelList()) {
            assertTrue(model.isValid(), model.getAlias() + ": object is invalid");
            assertTrue(validateAlias(model), model.getAlias() + ": invalid Alias: ");
            assertTrue(validateAdditionalComponents(model), model.getAlias() + ": invalid Additional Components");
            assertTrue(validateOptionalComponents(model), model.getAlias() + ": invalid Optional Components");
        }
    }

    private boolean validateAlias(DraftErrataModel model) {
        List<DraftItem> draftItems = DraftItem.generateAllCards();
        for (DraftItem item : draftItems) {
            if (item.getAlias().equals(model.getAlias())) {
                return true;
            }
        }
        System.out.println("FrankenErrata **" + model.getAlias() + "** failed validation due to invalid Alias: `"
                + model.getAlias() + "`");
        return false;
    }

    private boolean validateAdditionalComponents(DraftErrataModel model) {
        if (model.getAdditionalComponents() == null) {
            return true;
        }
        List<String> draftItems =
                DraftItem.generateAllCards().stream().map(DraftItem::getAlias).toList();
        List<String> additionalComponents = model.getAdditionalComponents().stream()
                .map(DraftErrataModel::getAlias)
                .toList();

        List<String> expectAddlComps = List.of("COMMANDER:ghostcommander");
        if (expectAddlComps.contains(model.getAlias()) && additionalComponents.isEmpty()) return false;
        if (draftItems.containsAll(additionalComponents)) {
            return true;
        }

        System.out.println("FrankenErrata **" + model.getAlias()
                + "** failed validation due to at least one invalid AdditionalComponent: `" + additionalComponents
                + "`");
        return false;
    }

    private boolean validateOptionalComponents(DraftErrataModel model) {
        if (model.getOptionalSwaps() == null) {
            return true;
        }
        List<String> draftItems =
                DraftItem.generateAllCards().stream().map(DraftItem::getAlias).toList();
        List<String> optionalComponents = model.getOptionalSwaps().stream()
                .map(DraftErrataModel::getAlias)
                .toList();

        List<String> expectOptSwaps = List.of("ABILITY:pillage");
        if (expectOptSwaps.contains(model.getAlias()) && optionalComponents.isEmpty()) return false;
        if (draftItems.containsAll(optionalComponents)) {
            return true;
        }

        System.out.println("FrankenErrata **" + model.getAlias()
                + "** failed validation due to at least one invalid OptionalComponent: `" + optionalComponents + "`");
        return false;
    }
}
