package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class BlankStoredValueSaveLoadTest extends BaseTi4Test {

    @Test
    void blankPlayerStoredValueSurvivesSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player player = game.getRealPlayers().getFirst();
            player.setStoredValue("blankKey", "");
            player.setStoredValue("keptKey", "kept");
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();

            assertThat(reloaded).isNotNull();
            Player reloadedPlayer = reloaded.getPlayer(player.getUserID());
            assertThat(reloadedPlayer.hasStoredValue("blankKey")).isFalse();
            assertThat(reloadedPlayer.getStoredValue("blankKey")).isEmpty();
            assertThat(reloadedPlayer.getStoredValue("keptKey")).isEqualTo("kept");
        }
    }

    @Test
    void settingBlankPlayerStoredValueRemovesExistingKey() {
        Player player = new Player("user-id", "user", new Game());
        player.setStoredValue("emptyKey", "something");
        player.setStoredValue("whitespaceKey", "something");
        player.setStoredValue("nullKey", "something");

        player.setStoredValue("emptyKey", "");
        player.setStoredValue("whitespaceKey", " \r\n");
        player.setStoredValue("nullKey", null);

        assertThat(player.getStoredValueMap()).doesNotContainKeys("emptyKey", "whitespaceKey", "nullKey");
        assertThat(player.getStoredValue("emptyKey")).isEmpty();
    }

    @Test
    void addingOnlyBlankEntriesToStoredListStoresNothing() {
        Player player = new Player("user-id", "user", new Game());

        player.addToStoredList("listKey", "");

        assertThat(player.hasStoredValue("listKey")).isFalse();
        assertThat(player.getStoredValueMap().values()).doesNotContain("");
    }

    @Test
    void legacyBlankPlayerStoredValueEntryLoads() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player player = game.getRealPlayers().getFirst();
            // Write straight into the map to reproduce saves made before setStoredValue dropped blank values.
            player.getStoredValueMap().put("legacyBlank", "");
            player.getStoredValueMap().put("keptKey", "kept");
            GameSaveService.save(game, "test");
            String saveFile = Files.readString(Storage.getGamePath(harness.getGameName() + Constants.TXT));
            assertThat(saveFile).contains(Constants.PLAYER_STORED_VALUES + " ", "legacyBlank,;keptKey,kept");

            Game reloaded = harness.load();

            assertThat(reloaded).isNotNull();
            Player reloadedPlayer = reloaded.getPlayer(player.getUserID());
            assertThat(reloadedPlayer.getStoredValue("legacyBlank")).isEmpty();
            assertThat(reloadedPlayer.getStoredValue("keptKey")).isEqualTo("kept");
        }
    }

    @Test
    void legacyBlankGameStoredValueEntryLoads() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            // Game.setStoredValue already refuses empty values, so bypass it to write the legacy shape.
            game.getStoredValueMap().put("legacyBlank", "");
            game.setStoredValue("keptKey", "kept");
            GameSaveService.save(game, "test");
            String saveFile = Files.readString(Storage.getGamePath(harness.getGameName() + Constants.TXT));
            assertThat(saveFile).contains("legacyBlank,:", "keptKey,kept:");

            Game reloaded = harness.load();

            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getStoredValue("legacyBlank")).isEmpty();
            assertThat(reloaded.getStoredValue("keptKey")).isEqualTo("kept");
        }
    }
}
