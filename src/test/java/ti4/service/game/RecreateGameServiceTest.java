package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class RecreateGameServiceTest extends BaseTi4Test {

    private final Guild originalPrimary = JdaService.guildPrimary;
    private final List<Guild> originalNewGameServers = new ArrayList<>(JdaService.serversToCreateNewGamesOn);

    @AfterEach
    void restoreJdaServiceGuildState() {
        JdaService.guildPrimary = originalPrimary;
        JdaService.serversToCreateNewGamesOn.clear();
        JdaService.serversToCreateNewGamesOn.addAll(originalNewGameServers);
    }

    @Test
    void sourceGameNameStripsTestSuffix() {
        assertEquals("pbd15036", RecreateGameService.getSourceGameName("pbd15036::test::abc"));
        assertEquals("pbd15036", RecreateGameService.getSourceGameName("pbd15036"));
    }

    @Test
    void testGameDetectionMatchesMarker() {
        assertTrue(RecreateGameService.isTestGame("pbd15036::test::abc"));
        assertFalse(RecreateGameService.isTestGame("pbd15036"));
    }

    @Test
    void sanitizedGameChannelPrefixRemovesUnsupportedCharacters() {
        assertEquals(
                "pbd15036-test-123e4567-e89b-12d3-a456-426614174000",
                RecreateGameService.getSanitizedGameChannelPrefix(
                        "pbd15036::test::123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void recreateGameReturnsIncompatibleMessageForFogOfWarGames() {
        Game game = mock(Game.class);
        Guild guild = mock(Guild.class);
        when(game.getName()).thenReturn("fow-game");
        when(game.isFowMode()).thenReturn(true);

        RecreateGameService.RecreateGameResult result = RecreateGameService.recreateGame(game, guild);

        assertEquals(
                "Could not recreate game resources for `fow-game`.\nNotes: Fog of War games are not compatible with recreate game and must be recreated manually.",
                result.getSummary());
    }

    @Test
    void resolveTargetGuildPrefersExistingLimboChannelGuild() {
        Game game = mock(Game.class);
        Guild preferredGuild = mockGuildWithCapacity("preferred", 10, 10);
        Guild limboGuild = mockGuildWithCapacity("limbo", 20, 20);
        TextChannel mainChannel = mock(TextChannel.class);
        Category limboCategory = mock(Category.class);

        when(game.getMainGameChannel()).thenReturn(mainChannel);
        when(game.getTableTalkChannel()).thenReturn(null);
        when(mainChannel.getParentCategory()).thenReturn(limboCategory);
        when(mainChannel.getGuild()).thenReturn(limboGuild);
        when(limboCategory.getName()).thenReturn(RecreateGameService.LIMBO_CATEGORY_NAME);

        assertSame(limboGuild, RecreateGameService.resolveTargetGuild(game, preferredGuild));
    }

    @Test
    void resolveTargetGuildUsesPreferredGuildWhenItHasCapacityAndNoChannelsExist() {
        Game game = mock(Game.class);
        Guild preferredGuild = mockGuildWithCapacity("preferred", 10, 10);

        when(game.getMainGameChannel()).thenReturn(null);
        when(game.getTableTalkChannel()).thenReturn(null);

        assertSame(preferredGuild, RecreateGameService.resolveTargetGuild(game, preferredGuild));
    }

    @Test
    void resolveTargetGuildFallsBackToMostCapableGuildWhenPreferredGuildIsFull() {
        Game game = mock(Game.class);
        Guild fullGuild = mockGuildWithCapacity("full", 250, 500);
        Guild fallbackGuild = mockGuildWithCapacity("fallback", 100, 100);
        Guild smallerFallbackGuild = mockGuildWithCapacity("smaller", 150, 200);

        when(game.getMainGameChannel()).thenReturn(null);
        when(game.getTableTalkChannel()).thenReturn(null);

        JdaService.serversToCreateNewGamesOn.clear();
        JdaService.serversToCreateNewGamesOn.add(smallerFallbackGuild);
        JdaService.serversToCreateNewGamesOn.add(fallbackGuild);
        JdaService.guildPrimary = null;

        assertSame(fallbackGuild, RecreateGameService.resolveTargetGuild(game, fullGuild));
    }

    private static Guild mockGuildWithCapacity(String name, int roleCount, int channelCount) {
        Guild guild = mock(Guild.class);
        when(guild.getName()).thenReturn(name);
        when(guild.getRoles())
                .thenReturn(
                        new ArrayList<>(Collections.nCopies(roleCount, mock(net.dv8tion.jda.api.entities.Role.class))));
        when(guild.getChannels())
                .thenReturn(new ArrayList<>(Collections.nCopies(
                        channelCount, mock(net.dv8tion.jda.api.entities.channel.middleman.GuildChannel.class))));
        return guild;
    }
}
