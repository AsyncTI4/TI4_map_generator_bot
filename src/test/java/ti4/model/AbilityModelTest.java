package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;

public class AbilityModelTest {

    @BeforeAll
    public static void init() {
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }

    @Test
    public void testAbilities() {
        for (AbilityModel model : Mapper.getAbilities().values()) {
            assertTrue(model.isValid());
        }
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
        String source = "testSource";
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
        abilityModel.setSource("testSource");
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
