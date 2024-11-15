package ti4.model;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActionCardModelTest extends BaseTi4Test {
    final ActionCardModel actionCardModel = new ActionCardModel();

    @Test
    // test a specific card
    public void testDirectHit() {
        ActionCardModel dhActionCardModel = Mapper.getActionCard("dh1");
        assertEquals("Direct Hit", dhActionCardModel.getName());
    }

    @Test
    public void testAlias() {
        String alias = "testAlias";
        actionCardModel.setAlias(alias);
        assertEquals(alias, actionCardModel.getAlias());
    }

    @Test
    public void testName() {
        String name = "testName";
        actionCardModel.setName(name);
        assertEquals(name, actionCardModel.getName());
    }

    @Test
    public void testPhase() {
        String phase = "testPhase";
        actionCardModel.setPhase(phase);
        assertEquals(phase, actionCardModel.getPhase());
    }

    @Test
    public void testWindow() {
        String window = "testWindow";
        actionCardModel.setWindow(window);
        assertEquals(window, actionCardModel.getWindow());
    }

    @Test
    public void testText() {
        String text = "testText";
        actionCardModel.setText(text);
        assertEquals(text, actionCardModel.getText());
    }

    @Test
    public void testFlavorText() {
        String flavorText = "testFlavorText";
        actionCardModel.setFlavorText(flavorText);
        assertEquals(flavorText, actionCardModel.getFlavorText().get());
    }

    @Test
    public void testSource() {
        ComponentSource source = ComponentSource.testsource;
        actionCardModel.setSource(source);
        assertEquals(ComponentSource.testsource, actionCardModel.getSource());
    }

    @Test
    public void testSearchTags() {
        List<String> searchTags = new ArrayList<>();
        searchTags.add("testTag1");
        searchTags.add("testTag2");
        actionCardModel.setSearchTags(searchTags);
        assertEquals(searchTags, actionCardModel.getSearchTags());
    }

    @Test
    public void testIsValid() {
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
    public void testSearch() {
        String searchString = "testAlias";
        actionCardModel.setAlias(searchString);
        assertTrue(actionCardModel.search(searchString.toLowerCase()));
    }

    @Test
    public void testAutoCompleteName() {
        String name = "testName";
        ComponentSource source = ComponentSource.testsource;
        actionCardModel.setName(name);
        actionCardModel.setSource(source);
        assertEquals(name + " (" + source + ")", actionCardModel.getAutoCompleteName());
    }
}
