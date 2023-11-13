package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AgendaModelTest {

    AgendaModel agendaModel = new AgendaModel();

    @BeforeEach
    public void setup() {
        agendaModel = new AgendaModel();
    }

    @Test
    public void testIsValid() {
        assertFalse(agendaModel.isValid());

        agendaModel.setAlias("testAlias");
        assertFalse(agendaModel.isValid());

        agendaModel.setName("testName");
        assertFalse(agendaModel.isValid());

        agendaModel.setCategory("faction");
        agendaModel.setCategoryDescription("testFaction");
        assertFalse(agendaModel.isValid());

        agendaModel.setType("testType");
        assertFalse(agendaModel.isValid());

        agendaModel.setText1("testText1");
        assertFalse(agendaModel.isValid());

        agendaModel.setSource("testSource");
        assertFalse(agendaModel.isValid());
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