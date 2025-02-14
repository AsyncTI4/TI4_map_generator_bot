package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

public class AbilityModelTest extends BaseTi4Test {
    @Test
    public void testAbilities() {
        for (AbilityModel model : Mapper.getAbilities().values()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validateFaction(model), model.getAlias() + ": invalid FactionID");
        }
    }

    private boolean validateFaction(AbilityModel model) {
        if (Mapper.isValidFaction(model.getFaction()) || "keleres".equals(model.getFaction())) return true;
        System.out.println("Ability **" + model.getAlias() + "** failed validation due to invalid FactionID: `"
                + model.getFaction() + "`");
        return false;
    }

    @Test
    public void testFaction() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        String arborec = "arborec";
        assertEquals(arborec, abilityModel.getFaction());
        String faction = "testFaction";
        abilityModel.setFaction(faction);
        assertEquals(faction, abilityModel.getFaction());
    }

    @Test
    public void testPermanentEffect() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        String permanentEffect = "Your space docks cannot produce infantry";
        assertEquals(Optional.of(permanentEffect), abilityModel.getPermanentEffect());
    }

    @Test
    public void testWindow() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        String window = "At the start of the status phase";
        assertEquals(Optional.of(window), abilityModel.getWindow());
    }

    @Test
    public void testWindowEffect() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        String windowEffect = "Place 1 infantry from your reinforcements on any planet you control.";
        assertEquals(Optional.of(windowEffect), abilityModel.getWindowEffect());
    }

    @Test
    public void testSource() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        ComponentSource source = ComponentSource.base;
        abilityModel.setSource(source);
        assertEquals(source, abilityModel.getSource());
    }

    @Test
    public void testSearchTags() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        List<String> searchTags = new ArrayList<>();
        searchTags.add("testTag1");
        searchTags.add("testTag2");
        abilityModel.setSearchTags(searchTags);
        assertEquals(searchTags, abilityModel.getSearchTags());
    }

    @Test
    public void testIsValid() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        abilityModel.setId("testId");
        abilityModel.setName("testName");
        abilityModel.setFaction("testFaction");
        abilityModel.setSource(ComponentSource.testsource);
        assertTrue(abilityModel.isValid());
    }

    @Test
    public void testGetAlias() {
        AbilityModel abilityModel = Mapper.getAbility("mitosis");
        String id = "testId";
        abilityModel.setId(id);
        assertEquals(id, abilityModel.getAlias());
    }
}
