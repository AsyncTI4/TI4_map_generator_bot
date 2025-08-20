package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

class ActionCardModelTest extends BaseTi4Test {
    private final ActionCardModel actionCardModel = new ActionCardModel();

    @Test
    // test a specific card
    void testDirectHit() {
        ActionCardModel dhActionCardModel = Mapper.getActionCard("dh1");
        assertEquals("Direct Hit", dhActionCardModel.getName());
    }

    @Test
    void testAlias() {
        String alias = "testAlias";
        actionCardModel.setAlias(alias);
        assertEquals(alias, actionCardModel.getAlias());
    }

    @Test
    void testName() {
        String name = "testName";
        actionCardModel.setName(name);
        assertEquals(name, actionCardModel.getName());
    }

    @Test
    void testPhase() {
        String phase = "testPhase";
        actionCardModel.setPhase(phase);
        assertEquals(phase, actionCardModel.getPhase());
    }

    @Test
    void testWindow() {
        String window = "testWindow";
        actionCardModel.setWindow(window);
        assertEquals(window, actionCardModel.getWindow());
    }

    @Test
    void testText() {
        String text = "testText";
        actionCardModel.setText(text);
        assertEquals(text, actionCardModel.getText());
    }

    @Test
    void testFlavorText() {
        String flavorText = "testFlavorText";
        actionCardModel.setFlavorText(flavorText);
        assertEquals(flavorText, actionCardModel.getFlavorText().get());
    }

    @Test
    void testSource() {
        ComponentSource source = ComponentSource.testsource;
        actionCardModel.setSource(source);
        assertEquals(ComponentSource.testsource, actionCardModel.getSource());
    }

    @Test
    void testSearchTags() {
        List<String> searchTags = new ArrayList<>();
        searchTags.add("testTag1");
        searchTags.add("testTag2");
        actionCardModel.setSearchTags(searchTags);
        assertEquals(searchTags, actionCardModel.getSearchTags());
    }

    @Test
    void testIsValid() {
        actionCardModel.setAlias("testAlias");
        actionCardModel.setName("testName");
        actionCardModel.setPhase("testPhase");
        actionCardModel.setWindow("testWindow");
        actionCardModel.setText("testText");
        actionCardModel.setFlavorText("testFlavorText");
        actionCardModel.setSource(ComponentSource.testsource);
        assertTrue(actionCardModel.isValid());
    }

    @Test
    void testSearch() {
        String searchString = "testAlias";
        actionCardModel.setAlias(searchString);
        assertTrue(actionCardModel.search(searchString.toLowerCase()));
    }

    @Test
    void testAutoCompleteName() {
        String name = "testName";
        ComponentSource source = ComponentSource.testsource;
        actionCardModel.setName(name);
        actionCardModel.setSource(source);
        assertEquals(name + " (" + source + ")", actionCardModel.getAutoCompleteName());
    }
}
