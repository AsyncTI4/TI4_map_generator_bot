package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.model.Source.ComponentSource;

public class AgendaModelTest {

    AgendaModel agendaModel = new AgendaModel();

    @BeforeAll
    public static void init() {
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }

    @BeforeEach
    public void setup() {
        agendaModel = new AgendaModel();
    }

    @Test
    public void testIsValid() {
        AgendaModel ixthian = Mapper.getAgenda("artifact");
        assertTrue(ixthian.isValid());

        ixthian.setAlias("testAlias");
        assertTrue(ixthian.isValid());

        ixthian.setName("testName");
        assertTrue(ixthian.isValid());

        ixthian.setCategory("agenda");
        ixthian.setCategoryDescription("testFaction");
        assertTrue(ixthian.isValid());

        ixthian.setType("testType");
        assertTrue(ixthian.isValid());

        ixthian.setText1("testText1");
        assertTrue(ixthian.isValid());

        ixthian.setSource(ComponentSource.testsource);
        assertTrue(ixthian.isValid());
    }

    @Test
    public void testValidateCategory() {
        agendaModel.setCategory("faction");
        agendaModel.setCategoryDescription("testFaction");
        assertFalse(agendaModel.isValid());

        agendaModel.setCategory("event");
        agendaModel.setCategoryDescription("immediate");
        assertFalse(agendaModel.isValid());

        agendaModel.setCategory("event");
        agendaModel.setCategoryDescription("permanent");
        assertFalse(agendaModel.isValid());

        agendaModel.setCategory("event");
        agendaModel.setCategoryDescription("temporary");
        assertFalse(agendaModel.isValid());

        agendaModel.setCategory("testCategory");
        assertFalse(agendaModel.isValid());
    }

    @Test
    public void testSearchTags() {
        List<String> searchTags = new ArrayList<>();
        searchTags.add("testTag1");
        searchTags.add("testTag2");
        agendaModel.setSearchTags(searchTags);
        assertEquals(searchTags, agendaModel.getSearchTags());
    }
}