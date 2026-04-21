package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.ShowGameService;
import ti4.service.fow.CreateFoWGameService;
import ti4.testUtils.BaseTi4Test;

class RecreateGameServiceTest extends BaseTi4Test {

    private Guild originalPrimary;
    private List<Guild> originalNewGameServers;

    @BeforeEach
    void setUpGuildState() {
        originalPrimary = JdaService.guildPrimary;
        originalNewGameServers = new ArrayList<>(JdaService.serversToCreateNewGamesOn);
    }

    @AfterEach
    void tearDownGuildState() {
        JdaService.guildPrimary = originalPrimary;
        JdaService.serversToCreateNewGamesOn.clear();
        JdaService.serversToCreateNewGamesOn.addAll(originalNewGameServers);
    }

    @Test
    void sourceGameNameStripsTestSuffix() {
        assertEquals("pbd15036", RecreateGameService.getSourceGameName("pbd15036-test-1"));
        assertEquals("pbd15036", RecreateGameService.getSourceGameName("pbd15036::test::abc"));
        assertEquals("pbd15036", RecreateGameService.getSourceGameName("pbd15036"));
    }

    @Test
    void testGameDetectionMatchesMarker() {
        assertTrue(RecreateGameService.isTestGame("pbd15036-test-1"));
        assertTrue(RecreateGameService.isTestGame("pbd15036::test::abc"));
        assertFalse(RecreateGameService.isTestGame("pbd15036"));
    }

    @Test
    void sanitizedGameChannelPrefixRemovesUnsupportedCharacters() {
        assertEquals("pbd15036-test-1", RecreateGameService.getSanitizedGameChannelPrefix("pbd15036-test-1"));
    }

    @Test
    void testGamesUseOnlyTheBaseNameForTheTableTalkChannel() {
        Game game = new Game();
        game.setName("pbd19815-test-1");
        game.setCustomName("gemini atmosphere omicron");

        assertEquals("pbd19815-test-1", RecreateGameService.getTableTalkChannelName(game));
    }

    @Test
    void recreatedResourcesAnnouncementUsesRunnerMentionWhenPresent() {
        Game game = mock(Game.class);
        Member member = mock(Member.class);
        RecreateGameService.RecreateGameResult result = new RecreateGameService.RecreateGameResult("pbd1");
        when(game.getPing()).thenReturn("@players");
        when(member.getAsMention()).thenReturn("<@123>");

        assertEquals(
                "<@123> this game's Discord resources were recreated.",
                RecreateGameService.getRecreatedResourcesAnnouncement(game, result, member));
    }

    @Test
    void postShowGameToBotMapThreadDelegatesToShowGameService() {
        Game game = mock(Game.class);
        ThreadChannel botThread = mock(ThreadChannel.class);

        try (MockedStatic<ShowGameService> showGameService = mockStatic(ShowGameService.class)) {
            RecreateGameService.postShowGameToBotMapThread(game, botThread);

            showGameService.verify(() -> ShowGameService.postShowGame(game, botThread));
        }
    }

    @Test
    void fogOfWarGamesUseAnonymousAnnouncementsChannelName() {
        Game game = mock(Game.class);
        when(game.isFowMode()).thenReturn(true);
        when(game.getName()).thenReturn("fow-game");

        assertEquals("fow-game-anonymous-announcements-private", RecreateGameService.getActionsChannelName(game));
    }

    @Test
    void fogOfWarGamesUseGmChannelName() {
        Game game = mock(Game.class);
        when(game.getName()).thenReturn("fow-game");

        assertEquals("fow-game-gm-room", RecreateGameService.getFogOfWarGmChannelName(game));
    }

    @Test
    void postShowGameToFowPrivateChannelsCallsShowGameForNonGmPlayers() {
        Game game = mock(Game.class);
        Player gm = mock(Player.class);
        Player player = mock(Player.class);
        TextChannel privateChannel = mock(TextChannel.class);

        when(game.getRealPlayers()).thenReturn(List.of(gm, player));
        when(gm.isGM()).thenReturn(true);
        when(player.isGM()).thenReturn(false);
        when(player.getPrivateChannel()).thenReturn(privateChannel);

        try (MockedStatic<ShowGameService> showGameService = mockStatic(ShowGameService.class)) {
            RecreateGameService.postShowGameToFogOfWarPrivateChannels(game);

            showGameService.verify(() -> ShowGameService.postShowGame(game, privateChannel, player));
        }
    }

    @Test
    void postShowGameToFowPrivateChannelsSkipsPlayersWithoutChannel() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);

        when(game.getRealPlayers()).thenReturn(List.of(player));
        when(player.isGM()).thenReturn(false);
        when(player.getPrivateChannel()).thenReturn(null);

        try (MockedStatic<ShowGameService> showGameService = mockStatic(ShowGameService.class)) {
            RecreateGameService.postShowGameToFogOfWarPrivateChannels(game);

            showGameService.verifyNoInteractions();
        }
    }

    @Test
    void ensureFogOfWarPlayerRoleSkipsGmPlayers() {
        Game game = mock(Game.class);
        Guild guild = mock(Guild.class);
        Role role = mock(Role.class);
        Player gm = mock(Player.class);
        Player player = mock(Player.class);
        Member member = mock(Member.class);
        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> roleAction = mock(AuditableRestAction.class);

        when(game.getName()).thenReturn("fow-game");
        when(game.getRealPlayers()).thenReturn(List.of(gm, player));
        when(gm.isGM()).thenReturn(true);
        when(player.isGM()).thenReturn(false);
        when(player.getUserID()).thenReturn("player-id");
        when(guild.getRolesByName("fow-game", true)).thenReturn(List.of(role));
        when(guild.getMemberById("player-id")).thenReturn(member);
        when(guild.addRoleToMember(member, role)).thenReturn(roleAction);

        RecreateGameService.ensureFogOfWarPlayerRole(
                game, guild, new RecreateGameService.RecreateGameResult("fow-game"));

        verify(guild).addRoleToMember(member, role);
        verify(gm, never()).getUserID();
    }

    @Test
    void ensureFogOfWarGmRoleAddsExistingGmsAndRunner() {
        Game game = mock(Game.class);
        Guild guild = mock(Guild.class);
        Role gmRole = mock(Role.class);
        Player gm = mock(Player.class);
        Member gmMember = mock(Member.class);
        Member runner = mock(Member.class);
        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> gmAction = mock(AuditableRestAction.class);
        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> runnerAction = mock(AuditableRestAction.class);

        when(game.getName()).thenReturn("fow-game");
        when(game.getPlayersWithGMRole()).thenReturn(List.of(gm));
        when(gm.getUserID()).thenReturn("gm-id");
        when(guild.getRolesByName("fow-game GM", true)).thenReturn(List.of(gmRole));
        when(guild.getMemberById("gm-id")).thenReturn(gmMember);
        when(guild.addRoleToMember(gmMember, gmRole)).thenReturn(gmAction);
        when(guild.addRoleToMember(runner, gmRole)).thenReturn(runnerAction);

        RecreateGameService.ensureFogOfWarGmRole(
                game, guild, runner, new RecreateGameService.RecreateGameResult("fow-game"));

        verify(guild).addRoleToMember(gmMember, gmRole);
        verify(guild).addRoleToMember(runner, gmRole);
    }

    @Test
    void ensureFogOfWarPrivateChannelsAddsNoteWhenGuildMemberIsMissing() {
        Game game = mock(Game.class);
        Guild guild = mock(Guild.class);
        Category category = mock(Category.class);
        Player player = mock(Player.class);
        RecreateGameService.RecreateGameResult result = new RecreateGameService.RecreateGameResult("fow-game");

        when(game.getRealPlayers()).thenReturn(List.of(player));
        when(player.isGM()).thenReturn(false);
        when(player.getPrivateChannel()).thenReturn(null);
        when(player.getUserID()).thenReturn("player-id");
        when(player.getUserName()).thenReturn("Player One");
        when(guild.getMemberById("player-id")).thenReturn(null);

        RecreateGameService.ensureFogOfWarPrivateChannels(game, guild, category, null, result);

        assertEquals(List.of("Private channel missing for Player One"), result.getNotes());
    }

    @Test
    void ensureFogOfWarPrivateChannelsSkipsCreationWithoutTargetCategory() {
        Game game = mock(Game.class);
        Guild guild = mock(Guild.class);
        Player player = mock(Player.class);
        RecreateGameService.RecreateGameResult result = new RecreateGameService.RecreateGameResult("fow-game");

        when(game.getRealPlayers()).thenReturn(List.of(player));
        when(player.isGM()).thenReturn(false);
        when(player.getPrivateChannel()).thenReturn(null);

        try (MockedStatic<CreateFoWGameService> createFoWGameService = mockStatic(CreateFoWGameService.class)) {
            RecreateGameService.ensureFogOfWarPrivateChannels(game, guild, null, null, result);

            createFoWGameService.verifyNoInteractions();
        }
        assertTrue(result.getCreatedChannels().isEmpty());
        assertTrue(result.getNotes().isEmpty());
    }

    @Test
    void ensureFogOfWarPrivateChannelsCreatesMissingPrivateChannel() {
        Game game = mock(Game.class);
        Guild guild = mock(Guild.class);
        Category category = mock(Category.class);
        Player player = mock(Player.class);
        Member member = mock(Member.class);
        TextChannel privateChannel = mock(TextChannel.class);
        RecreateGameService.RecreateGameResult result = new RecreateGameService.RecreateGameResult("fow-game");

        when(game.getRealPlayers()).thenReturn(List.of(player));
        when(player.isGM()).thenReturn(false);
        when(player.getPrivateChannel()).thenReturn(null, privateChannel);
        when(player.getUserID()).thenReturn("player-id");
        when(guild.getMemberById("player-id")).thenReturn(member);
        when(category.getId()).thenReturn("category-id");
        when(privateChannel.getName()).thenReturn("player-private");
        when(privateChannel.getParentCategory()).thenReturn(category);
        when(privateChannel.getParentCategoryId()).thenReturn("category-id");

        try (MockedStatic<CreateFoWGameService> createFoWGameService = mockStatic(CreateFoWGameService.class)) {
            RecreateGameService.ensureFogOfWarPrivateChannels(game, guild, category, null, result);

            createFoWGameService.verify(() -> CreateFoWGameService.createPrivateChannelForPlayer(member, game));
        }
        assertEquals(List.of("player-private"), result.getCreatedChannels());
    }

    @Test
    void resolveTargetGuildFallsBackToMostCapableGuildWhenPreferredGuildIsNull() {
        Game game = mock(Game.class);
        Guild fallbackGuild = mockGuildWithCapacity("fallback", 100, 100);
        Guild smallerFallbackGuild = mockGuildWithCapacity("smaller", 150, 200);

        when(game.getMainGameChannel()).thenReturn(null);
        when(game.getTableTalkChannel()).thenReturn(null);

        JdaService.serversToCreateNewGamesOn.clear();
        JdaService.serversToCreateNewGamesOn.add(smallerFallbackGuild);
        JdaService.serversToCreateNewGamesOn.add(fallbackGuild);
        JdaService.guildPrimary = null;

        assertSame(fallbackGuild, RecreateGameService.resolveTargetGuild(game, null));
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
                .thenReturn(IntStream.range(0, roleCount)
                        .mapToObj(index -> mock(Role.class))
                        .collect(Collectors.toCollection(ArrayList::new)));
        when(guild.getChannels())
                .thenReturn(IntStream.range(0, channelCount)
                        .mapToObj(index -> mock(GuildChannel.class))
                        .collect(Collectors.toCollection(ArrayList::new)));
        return guild;
    }
}
