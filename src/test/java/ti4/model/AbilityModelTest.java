package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;

public class AbilityModelTest {
    
    AbilityModel abilityModel = Mapper.getAbility("mitosis");

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
        String permanentEffect = "testPermanentEffect";
        abilityModel.setPermanentEffect(permanentEffect);
        assertEquals(Optional.of(permanentEffect), abilityModel.getPermanentEffect());
    }

    @Test
    public void testWindow() {
        String window = "testWindow";
        abilityModel.setWindow(window);
        assertEquals(Optional.of(window), abilityModel.getWindow());
    }

    @Test
    public void testWindowEffect() {
        String windowEffect = "testWindowEffect";
        abilityModel.setWindowEffect(windowEffect);
        assertEquals(Optional.of(windowEffect), abilityModel.getWindowEffect());
    }

    @Test
    public void testSource() {
        String source = "testSource";
        abilityModel.setSource(source);
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