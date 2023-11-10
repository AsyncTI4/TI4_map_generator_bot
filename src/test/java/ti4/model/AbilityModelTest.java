package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;

public class AbilityModelTest {
    
    @BeforeAll
    public static void init() {
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }
    AbilityModel abilityModel = Mapper.getAbility("mitosis");

    @Test
    public void testAbilities() {
        for (AbilityModel model : Mapper.getAbilities().values()) {
            assertTrue(model.isValid());
        }
    }

    @Test
    public void testFaction() {
        String arborec = "arborec";
        assertEquals(arborec, abilityModel.getFaction());
        String faction = "testFaction";
        abilityModel.setFaction(faction);
        assertEquals(faction, abilityModel.getFaction());
    }

    @Test
    public void testPermanentEffect() {
        String permanentEffect = "Your space docks cannot produce infantry";
        assertEquals(Optional.of(permanentEffect), abilityModel.getPermanentEffect());
    }

    @Test
    public void testWindow() {
        String window = "At the start of the status phase";
        assertEquals(Optional.of(window), abilityModel.getWindow());
    }

    @Test
    public void testWindowEffect() {
        String windowEffect = "Place 1 infantry from your reinforcements on any planet you control.";
        assertEquals(Optional.of(windowEffect), abilityModel.getWindowEffect());
    }

    @Test
    public void testSource() {
        String source = "testSource";
        assertEquals(source, abilityModel.getSource());
    }

    @Test
    public void testSearchTags() {
        List<String> searchTags = new ArrayList<>();
        searchTags.add("testTag1");
        searchTags.add("testTag2");
        abilityModel.setSearchTags(searchTags);
        assertEquals(searchTags, abilityModel.getSearchTags());
    }

    @Test
    public void testIsValid() {
        abilityModel.setId("testId");
        abilityModel.setName("testName");
        abilityModel.setFaction("testFaction");
        abilityModel.setSource("testSource");
        assertTrue(abilityModel.isValid());
    }

    @Test
    public void testGetAlias() {
        String id = "testId";
        abilityModel.setId(id);
        assertEquals(id, abilityModel.getAlias());
    }

}