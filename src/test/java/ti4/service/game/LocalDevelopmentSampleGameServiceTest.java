package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class LocalDevelopmentSampleGameServiceTest extends BaseTi4Test {

    private String originalResourcePath;

    @BeforeEach
    void captureResourcePath() {
        originalResourcePath = Storage.getResourcePath();
    }

    @AfterEach
    void restoreResourcePath() {
        Storage.setResourcePath(originalResourcePath);
    }

    @Test
    void prepareClonedGameRenamesGameAndClearsDiscordIds() {
        Game game = new Game();
        game.setName("source");
        game.setHasEnded(true);
        game.setEndedDate(123L);
        game.setTableTalkChannelID("table");
        game.setMainChannelID("main");
        game.setSavedChannelID("saved");
        game.setSavedMessage("message");
        game.setBotMapUpdatesThreadID("bot");
        game.setLaunchPostThreadID("launch");
        Player player = game.addPlayer("1", "player");
        player.setRoleIDForCommunity("role");
        player.setPrivateChannelID("private");
        player.setCardsInfoThreadID("cards");
        player.setBagInfoThreadID("bag");

        LocalDevelopmentSampleGameService.prepareClonedGame(game, "source-test-1");

        assertEquals("source-test-1", game.getName());
        assertFalse(game.isHasEnded());
        assertEquals(0, game.getEndedDate());
        assertNull(game.getTableTalkChannelID());
        assertNull(game.getMainChannelID());
        assertNull(game.getSavedChannelID());
        assertNull(game.getSavedMessage());
        assertNull(game.getBotMapUpdatesThreadID());
        assertNull(game.getLaunchPostThreadID());
        assertNull(player.getRoleIDForCommunity());
        assertNull(player.getPrivateChannelID());
        assertNull(player.getCardsInfoThreadID());
        assertNull(player.getBagInfoThreadID());
    }

    @Test
    void isLocalDevelopmentStartupRequiresExplicitOptIn() {
        assertFalse(LocalDevelopmentSampleGameService.isLocalDevelopmentStartup(null));
        assertFalse(LocalDevelopmentSampleGameService.isLocalDevelopmentStartup("false"));
        assertTrue(LocalDevelopmentSampleGameService.isLocalDevelopmentStartup("true"));
    }

    @Test
    void startupSourceGameDefaults() {
        assertEquals(
                LocalDevelopmentSampleGameService.DEFAULT_SOURCE_GAME_NAME,
                LocalDevelopmentSampleGameService.getStartupSourceGameName());
    }

    @Test
    void formatTestGameNameUsesMarkerAndIncrementingSuffix() {
        assertEquals("pbd15036-test-42", LocalDevelopmentSampleGameService.formatTestGameName("pbd15036", 42));
    }

    @Test
    void buildTestGameNameSkipsExistingStoredFile() throws Exception {
        String sourceGameName = "localdevnaming";
        Path existingPath = Storage.getGamePath(
                LocalDevelopmentSampleGameService.formatTestGameName(sourceGameName, 1) + Constants.TXT);
        Files.createDirectories(existingPath.getParent());
        Files.deleteIfExists(existingPath);
        Files.write(existingPath, java.util.List.of("owner-id", "owner-name", "game-name"));
        try {
            assertEquals("localdevnaming-test-2", LocalDevelopmentSampleGameService.buildTestGameName(sourceGameName));
        } finally {
            Files.deleteIfExists(existingPath);
        }
    }

    @Test
    void copySourceGameToStorageCopiesFileAndUpdatesStoredGameName(@TempDir Path tempDirectory) throws Exception {
        Path sourcePath = tempDirectory.resolve("source.txt");
        Path targetPath = tempDirectory.resolve("target.txt");
        Files.write(sourcePath, java.util.List.of("owner-id", "owner-name", "source-game", "rest"));

        assertTrue(LocalDevelopmentSampleGameService.copySourceGameToStorage(
                sourcePath, targetPath, "source-game-test-1"));

        assertEquals(
                java.util.List.of("owner-id", "owner-name", "source-game-test-1", "rest"),
                Files.readAllLines(targetPath));
    }

    @Test
    void importSourceGameFileStoresUploadedFileUsingEmbeddedGameName(@TempDir Path tempDirectory) throws Exception {
        Path mainResources = tempDirectory.resolve("main").resolve("resources");
        Storage.setResourcePath(mainResources.toString());
        Path uploadedSourceFile = tempDirectory.resolve("upload.txt");
        Files.write(uploadedSourceFile, java.util.List.of("owner-id", "owner-name", "embedded-game-name", "rest"));

        assertEquals("embedded-game-name", LocalDevelopmentSampleGameService.importSourceGameFile(uploadedSourceFile));
        assertEquals(
                java.util.List.of("owner-id", "owner-name", "embedded-game-name", "rest"),
                Files.readAllLines(LocalDevelopmentSampleGameService.getMapsSourcePath("embedded-game-name.txt")));
    }

    @Test
    void readGameNameFromSourceFileReturnsNullForMalformedFile(@TempDir Path tempDirectory) throws Exception {
        Path uploadedSourceFile = tempDirectory.resolve("upload.txt");
        Files.write(uploadedSourceFile, java.util.List.of("owner-id", "owner-name"));

        assertNull(LocalDevelopmentSampleGameService.readGameNameFromSourceFile(uploadedSourceFile));
    }
}
