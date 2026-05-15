package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.helper.GameHelper;
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
    void shouldMigrateLegacyCreationDateWhenCreationDateTimeIsMissing() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Path gameFile = Storage.getGamePath(harness.getGameName() + Constants.TXT);
            Files.write(
                    gameFile,
                    Files.readAllLines(gameFile).stream()
                            .filter(line -> !line.startsWith(Constants.CREATION_DATE_TIME + " "))
                            .map(line -> line.startsWith(Constants.CREATION_DATE + " ")
                                    ? Constants.CREATION_DATE + " 2025.10.28"
                                    : line)
                            .toList());

            Game game = harness.load();

            assertThat(game).isNotNull();
            assertThat(game.getCreationDateTime())
                    .isEqualTo(GameHelper.getCreationDateTimeFromLegacyDate("2025.10.28"));
        }
    }
}
