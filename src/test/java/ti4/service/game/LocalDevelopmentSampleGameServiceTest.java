package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class LocalDevelopmentSampleGameServiceTest extends BaseTi4Test {

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

        LocalDevelopmentSampleGameService.prepareClonedGame(game, "source::test::uuid");

        assertEquals("source::test::uuid", game.getName());
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
    void startupSourceGameDefaultsAndRespectsEnvOverride() {
        assertEquals(
                LocalDevelopmentSampleGameService.DEFAULT_SOURCE_GAME_NAME,
                LocalDevelopmentSampleGameService.getStartupSourceGameName("true", null));
        assertEquals(
                "pbd42",
                LocalDevelopmentSampleGameService.getStartupSourceGameName("true", "pbd42"));
        assertEquals(
                LocalDevelopmentSampleGameService.DEFAULT_SOURCE_GAME_NAME,
                LocalDevelopmentSampleGameService.getStartupSourceGameName("false", "pbd42"));
    }

    @Test
    void buildTestGameNameUsesMarkerAndUuid() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals(
                "pbd15036::test::123e4567-e89b-12d3-a456-426614174000",
                LocalDevelopmentSampleGameService.buildTestGameName("pbd15036", uuid));
    }

    @Test
    void copySourceGameToStorageCopiesFileAndUpdatesStoredGameName(@TempDir Path tempDirectory) throws Exception {
        Path sourcePath = tempDirectory.resolve("source.txt");
        Path targetPath = tempDirectory.resolve("target.txt");
        Files.write(sourcePath, java.util.List.of("owner-id", "owner-name", "source-game", "rest"));

        assertTrue(LocalDevelopmentSampleGameService.copySourceGameToStorage(
                sourcePath, targetPath, "source-game::test::uuid"));

        assertEquals(
                java.util.List.of("owner-id", "owner-name", "source-game::test::uuid", "rest"),
                Files.readAllLines(targetPath));
    }
}
