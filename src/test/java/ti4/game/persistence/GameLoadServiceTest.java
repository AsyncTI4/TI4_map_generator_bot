package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class GameLoadServiceTest extends BaseTi4Test {

    @Test
    void shouldLoadGameFromFile() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();

            assertThat(game).isNotNull();
            assertThat(game.getName()).isEqualTo(harness.getGameName());
        }
    }

    @Test
    void shouldLoadLegacyRulesLinksSettingAsOptedOut() throws IOException {
        // Saves from before the inverted check was fixed hold "inject_rules_links true" for almost
        // every game, which at the time meant no links. Honouring it under the fixed check would
        // switch links on across every existing game, so the old key is ignored and the game loads
        // with links off until someone opts in again.
        try (var harness = TestGameHarness.forDefaultMap()) {
            assertThat(Files.readAllLines(Storage.getGamePath(harness.getGameName() + Constants.TXT)))
                    .contains("inject_rules_links true");

            assertThat(harness.load().isInjectRulesLinks()).isFalse();
        }
    }
}
