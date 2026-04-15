package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class LocalDevelopmentSampleGameServiceTest extends BaseTi4Test {

    @Test
    void copySampleGameFileIfMissingCopiesMissingFileWithoutOverwriting() throws Exception {
        Path tempDirectory = Files.createTempDirectory("local-dev-sample");
        Path sourceFile = tempDirectory.resolve("source.txt");
        Path targetFile = tempDirectory.resolve("target.txt");

        Files.writeString(sourceFile, "sample");
        LocalDevelopmentSampleGameService.copySampleGameFileIfMissing(sourceFile, targetFile);
        assertEquals("sample", Files.readString(targetFile));

        Files.writeString(targetFile, "existing");
        LocalDevelopmentSampleGameService.copySampleGameFileIfMissing(sourceFile, targetFile);
        assertEquals("existing", Files.readString(targetFile));
    }

    @Test
    void prepareGameForLocalDevelopmentClearsEndedState() {
        Game game = new Game();
        game.setHasEnded(true);
        game.setEndedDate(123L);

        LocalDevelopmentSampleGameService.prepareGameForLocalDevelopment(game);

        assertFalse(game.isHasEnded());
        assertEquals(0, game.getEndedDate());
    }

    @Test
    void isLocalDevelopmentStartupRequiresSingleGuildLaunchArguments() {
        assertTrue(
                LocalDevelopmentSampleGameService.isLocalDevelopmentStartup(new String[] {"token", "user", "guild"}));
        assertFalse(LocalDevelopmentSampleGameService.isLocalDevelopmentStartup(new String[] {"token", "user"}));
        assertFalse(LocalDevelopmentSampleGameService.isLocalDevelopmentStartup(
                new String[] {"token", "user", "guild", "community"}));
    }
}
