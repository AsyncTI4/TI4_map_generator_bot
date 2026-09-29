package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.message.MessageHelper;
import ti4.service.tigl.TiglPlayerRankHistory;
import ti4.service.tigl.TiglRankEntry;
import ti4.service.tigl.TiglRankHistoryResponse;
import ti4.testUtils.BaseTi4Test;

/**
 * Covers the rank snapshot taken when a game is flagged as TIGL. The interesting cases are all about players
 * whose Discord {@link User} can't be resolved: {@code Player.getUser()} returns null for any uncached id,
 * which includes the neutral "Dicecord" dummy that every Fog of War game carries.
 */
class TIGLHelperTest extends BaseTi4Test {

    private static final String HUB_MEMBER_ID = "111";
    private static final String UNRESOLVABLE_ID = "222";
    private static final String DUMMY_ID = "333";

    private JDA originalJda;
    private Guild originalGuild;

    /**
     * BaseTi4Test installs process-wide mocks, so stub local ones and put the originals back - otherwise these
     * user-resolution stubs would leak into every other test class that reads JdaService.
     */
    @BeforeEach
    void installJdaStubs() {
        originalJda = JdaService.jda;
        originalGuild = JdaService.guildPrimary;

        JDA jda = mock(JDA.class);
        // Every id resolves to a user except the two that stand in for an uncached account.
        when(jda.getUserById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if (UNRESOLVABLE_ID.equals(id) || DUMMY_ID.equals(id)) return null;
            User user = mock(User.class);
            when(user.getId()).thenReturn(id);
            return user;
        });
        Guild guild = mock(Guild.class);
        // A mock Member with no roles is enough: it makes the player a confirmed hub member of no known rank.
        when(guild.getMemberById(anyString())).thenReturn(mock(Member.class));

        JdaService.jda = jda;
        JdaService.guildPrimary = guild;
    }

    @AfterEach
    void restoreJda() {
        JdaService.jda = originalJda;
        JdaService.guildPrimary = originalGuild;
    }

    private static Game tiglGame() {
        Game game = new Game();
        game.setName("tigl-test");
        return game;
    }

    /** Spectators and factionless entries are not league participants, so a test player needs a real seat. */
    private static Player seat(Game game, String userId, String name) {
        Player player = game.addPlayer(userId, name);
        player.setFaction("sol");
        player.setColor("blue");
        return player;
    }

    private static final long GAME_START = LocalDate.parse("2026-02-01")
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli();

    private static Map<Long, TiglPlayerRankHistory> historyFor(long userId, String league, String date, String rank) {
        TiglRankEntry entry = new TiglRankEntry();
        entry.setLeague(league);
        entry.setDate(date);
        entry.setRankName(rank);

        TiglPlayerRankHistory history = new TiglPlayerRankHistory();
        history.setDiscordUserId(userId);
        history.setRanks(List.of(entry));
        return Map.of(userId, history);
    }

    // The league's rank names are the Discord role names minus the "TIGL - " prefix, because Lazik's site assigns
    // those roles from this same data - so they map straight onto the enum.
    @Test
    void mapsLeagueRankNamesOntoTheEnum() {
        Game game = tiglGame();
        Player player = seat(game, "111", "real");

        var standard = historyFor(111L, "Standard", "2026-01-01", "Commander");
        assertEquals(TIGLRank.COMMANDER, TIGLHelper.rankAtGameStart(standard, player, "Standard", GAME_START));

        var fractured = historyFor(111L, "Fractured", "2026-01-01", "Thrall");
        assertEquals(TIGLRank.THRALL, TIGLHelper.rankAtGameStart(fractured, player, "Fractured", GAME_START));
    }

    // Profiles are created once a game reaches round 2, so a brand new player legitimately has no history at all.
    @Test
    void treatsAPlayerWithNoLeagueProfileAsUnranked() {
        Game game = tiglGame();
        Player player = seat(game, "111", "real");

        assertEquals(TIGLRank.UNRANKED, TIGLHelper.rankAtGameStart(Map.of(), player, "Standard", GAME_START));
    }

    // A rank earned after the game started must not be applied retroactively.
    @Test
    void ignoresRanksEarnedAfterTheGameStarted() {
        Game game = tiglGame();
        Player player = seat(game, "111", "real");

        var laterRankUp = historyFor(111L, "Standard", "2026-03-01", "Hero");
        assertEquals(TIGLRank.UNRANKED, TIGLHelper.rankAtGameStart(laterRankUp, player, "Standard", GAME_START));
    }

    // Composite prestige names only ever appear in currentRanks, which we do not read - but if one leaked into the
    // history it must not silently become some unrelated rank.
    @Test
    void resolvesAPrestigeNameToItsLadderRank() {
        Game game = tiglGame();
        Player player = seat(game, "111", "real");

        // The league returns prestige titles in the dated history with the ladder rank in brackets. Reading only the
        // enum constant names resolved this to null, which then fell through to UNRANKED and became the game's
        // minimum rank - recording a Hero-level game as Unranked.
        var prestige = historyFor(111L, "Standard", "2026-01-01", "Galactic Threat II (Hero)");
        assertEquals(TIGLRank.HERO, TIGLHelper.rankAtGameStart(prestige, player, "Standard", GAME_START));
    }

    @Test
    void stillFallsBackToUnrankedForANameWithNoLadderRankInIt() {
        Game game = tiglGame();
        Player player = seat(game, "111", "real");

        var nonsense = historyFor(111L, "Standard", "2026-01-01", "Some Title We Have Never Heard Of");
        assertEquals(TIGLRank.UNRANKED, TIGLHelper.rankAtGameStart(nonsense, player, "Standard", GAME_START));
    }

    @Test
    void resolvesEveryLadderRankFromAPrestigeWrapper() {
        for (TIGLRank rank : TIGLRank.values()) {
            if (rank.getIndex() == -1) continue;
            assertEquals(rank, TIGLHelper.resolveLeagueRankName("Some Prestige Title (" + rank.getShortName() + ")"));
        }
    }

    // Every path into a TIGL game funnels through markAsTIGLGame, and a forum-created game hits it twice: once at
    // creation and again when the table confirms the ladder. Without the transition guard the table talk channel
    // gets the Code of Conduct wall posted two or three times within minutes of each other.
    @Test
    void theCodeOfConductBannerIsPostedOnlyOnTheTransitionIntoTigl() {
        Game game = tiglGame();
        seat(game, HUB_MEMBER_ID, "real");

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            assertTrue(TIGLHelper.markAsTIGLGame(game, false));
            assertTrue(TIGLHelper.markAsTIGLGame(game, true));
            assertTrue(TIGLHelper.markAsTIGLGame(game, false));

            messages.verify(
                    () -> MessageHelper.sendMessageToChannel(
                            any(), argThat(text -> text != null && text.contains("Code of Conduct"))),
                    times(1));
        }
    }

    // The guard keys on the flag, not on the tag, so a ladder switch still has to move the tag.
    @Test
    void aLadderSwitchStillRetagsTheGame() {
        Game game = tiglGame();
        seat(game, HUB_MEMBER_ID, "real");

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            TIGLHelper.markAsTIGLGame(game, false);
            assertTrue(!TIGLHelper.isFracturedTIGLGame(game));

            TIGLHelper.markAsTIGLGame(game, true);
            assertTrue(TIGLHelper.isFracturedTIGLGame(game));
        }
    }

    // The API returns bare display names ("Hero", "Galactic Emperor"). Every Standard rank happened to parse because
    // its enum constant name matched its display word - except EMPEROR, whose display name is "Galactic Emperor".
    // It resolved to null and then fell through to UNRANKED, dragging the whole game's minimum rank to the bottom.
    @Test
    void everyLadderRankResolvesFromTheDisplayNameTheApiReturns() {
        for (TIGLRank rank : TIGLRank.values()) {
            if (rank.getIndex() == -1) continue; // faction hero ranks are not ladder ranks
            assertEquals(
                    rank,
                    TIGLRank.fromString(rank.getShortName()),
                    "could not resolve " + rank.getShortName() + " as returned by the league API");
        }
    }

    @Test
    void theTopStandardRankIsNotSilentlyUnranked() {
        assertEquals(TIGLRank.EMPEROR, TIGLRank.fromString("Galactic Emperor"));
    }

    // Every Standard/Fractured rank name in the live capture must map to a TIGLRank. One that does not resolves to
    // UNRANKED and becomes the game's minimum rank, reporting the whole game at the bottom of the ladder.
    @Test
    void everyRankNameTheLeagueActuallyReturnsResolves() throws Exception {
        TiglRankHistoryResponse response;
        try (java.io.InputStream in = TIGLHelperTest.class.getResourceAsStream("/tigl/rank-history-live.json")) {
            response = ti4.json.JsonMapperManager.basic().readValue(in, TiglRankHistoryResponse.class);
        }

        List<String> unresolvable = response.getData().getItems().stream()
                .flatMap(history -> history.getRanks().stream())
                .filter(entry -> "Standard".equalsIgnoreCase(entry.getLeague())
                        || "Fractured".equalsIgnoreCase(entry.getLeague()))
                .map(TiglRankEntry::getRankName)
                .distinct()
                .filter(name -> TIGLHelper.resolveLeagueRankName(name) == null)
                .toList();

        assertEquals(List.of(), unresolvable, "rank names the bot cannot map");
    }

    // Galactic Threat and Tyrant are prestige: on gaining one you lose your ladder ranks and climb again. The league
    // reports that as "<prestige> (<current ladder rank>)", so the bracketed part is the rank a game must use. A bare
    // prestige title means the player has not climbed back yet, and Unranked is then the correct answer rather than a
    // rank the bot failed to recognise.
    @Test
    void aPrestigeHolderIsRankedByWhereTheyAreOnTheLadderNow() {
        assertEquals(TIGLRank.HERO, TIGLHelper.resolveLeagueRankName("Galactic Threat II (Hero)"));
        assertEquals(TIGLRank.COMMANDER, TIGLHelper.resolveLeagueRankName("Galactic Threat I (Commander)"));
        assertEquals(TIGLRank.ACOLYTE, TIGLHelper.resolveLeagueRankName("Tyrant I (Acolyte)"));

        // Reset and not yet climbed back.
        assertNull(TIGLHelper.resolveLeagueRankName("Galactic Threat I"));
        assertNull(TIGLHelper.resolveLeagueRankName("Tyrant II"));
    }
}
