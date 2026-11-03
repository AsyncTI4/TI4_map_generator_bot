package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class GameRevisionKeyFileTest extends BaseTi4Test {

    @Test
    void backfillWritesOneKeyThatLoadingAndLaterReadsSee() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            String gameName = harness.getGameName();
            List<String> linesBefore = Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8);
            assertThat(GameRevisionKeyFile.read(gameName)).isNull();

            String revisionKey = GameRevisionKeyFile.backfill(gameName);

            assertThat(revisionKey).isNotBlank();
            assertThat(GameRevisionKeyFile.read(gameName)).isEqualTo(revisionKey);
            assertThat(GameRevisionKeyFile.backfill(gameName)).isEqualTo(revisionKey);
            Game game = GameLoadService.load(gameName);
            assertThat(game).isNotNull();
            assertThat(game.getRevisionKey()).isEqualTo(revisionKey);

            // Only the key line is added; every other line of the save is untouched.
            List<String> linesAfter = Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8);
            List<String> withoutKey = new ArrayList<>(linesAfter);
            withoutKey.remove(GamePersistenceKeys.REVISION_KEY + " " + revisionKey);
            assertThat(withoutKey).isEqualTo(linesBefore);
        }
    }

    @Test
    void blankKeyLineIsReplacedRatherThanDuplicated() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            List<String> lines = new ArrayList<>(Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8));
            lines.add(lines.indexOf(GamePersistenceKeys.GAMEINFO) + 1, GamePersistenceKeys.REVISION_KEY + " ");
            Files.write(gamePath(harness), lines, StandardCharsets.UTF_8);
            assertThat(GameRevisionKeyFile.read(harness.getGameName())).isNull();

            String revisionKey = GameRevisionKeyFile.backfill(harness.getGameName());

            List<String> keyLines = Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8).stream()
                    .filter(line -> line.startsWith(GamePersistenceKeys.REVISION_KEY))
                    .toList();
            assertThat(keyLines).containsExactly(GamePersistenceKeys.REVISION_KEY + " " + revisionKey);
        }
    }

    @Test
    void savedGameKeepsTheKeyItWasSavedWith() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = GameLoadService.load(harness.getGameName());
            GameSaveService.save(game, "test");

            assertThat(GameRevisionKeyFile.read(harness.getGameName())).isEqualTo(game.getRevisionKey());
        }
    }

    @Test
    void missingGameHasNoKeyAndIsNotCreated() {
        String gameName = "missing-" + UUID.randomUUID();

        assertThat(GameRevisionKeyFile.read(gameName)).isNull();
        assertThat(GameRevisionKeyFile.backfill(gameName)).isNull();
        assertThat(GameRevisionKeyFile.read(gameName)).isNull();
    }

    private static Path gamePath(TestGameHarness harness) {
        return Storage.getGamePath(harness.getGameName() + Constants.TXT);
    }
}
