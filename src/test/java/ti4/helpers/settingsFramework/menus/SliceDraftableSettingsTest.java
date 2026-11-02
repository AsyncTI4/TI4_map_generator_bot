package ti4.helpers.settingsFramework.menus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SliceDraftableSettingsTest extends BaseTi4Test {

    // Nucleus templates only exist for 3-8 players, but the nucleus preset is allowed to start with more
    // seated players (it only warns), so saved nucleus settings must reload for a 9+ player game.
    @Test
    void savedNucleusSettingsReloadWhenNoNucleusTemplateFitsPlayerCount() {
        Game game = new Game();
        for (int i = 0; i < 9; i++) {
            game.addPlayer("user" + i, "player" + i);
        }
        JsonNode json = JsonMapper.builder().build().readTree("""
                        {
                          "menuId": "dsSlice",
                          "mapTemplate": {"id": "Template", "chosenKey": "8pHyperlanesNucleus"},
                          "mapGenerationMode": {"id": "MapGenMode", "chosenKey": "Nucleus"}
                        }
                        """);

        SliceDraftableSettings settings = assertDoesNotThrow(() -> new SliceDraftableSettings(game, json, null));

        assertEquals("8pHyperlanesNucleus", settings.getMapTemplate().getChosenKey());
    }
}
