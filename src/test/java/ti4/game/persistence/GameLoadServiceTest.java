package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;
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
    void shouldLoadLeaderTypeFromSavedDataWhenIdNoLongerIncludesType() throws Exception {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            var gamePath = Storage.getGamePath(harness.getGameName() + Constants.TXT);
            List<String> lines = Files.readAllLines(gamePath);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.matches("^leaders [^,]+,agent,.*")) {
                    lines.set(i, line.replaceFirst("^leaders [^,]+,agent,", "leaders legacyleader,agent,"));
                    break;
                }
            }
            Files.write(gamePath, lines);

            Game game = harness.load();
            assertThat(game.getPlayers().values()).anySatisfy(player -> assertThat(player.getLeaderByID("legacyleader"))
                    .hasValueSatisfying(leader -> assertThat(leader.getType()).isEqualTo("agent")));
        }
    }
}
