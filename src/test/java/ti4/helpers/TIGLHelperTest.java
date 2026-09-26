package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TIGLHelper.TIGLRank;
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

    @Test
    void snapshotIgnoresDummiesInsteadOfFailingOnTheirMissingUser() {
        Game game = tiglGame();
        Player real = game.addPlayer(HUB_MEMBER_ID, "real");
        Player dummy = game.addPlayer(DUMMY_ID, "Dicecord");
        dummy.setDummy(true);

        TIGLHelper.initializeTIGLGame(game, true);

        // A non-null minimum rank means we got past the hub-membership check rather than bailing out of rank
        // handling - the dummy's unresolvable user used to NPE here, and counting it as a non-hub member would
        // have disabled ranks for the whole game.
        assertEquals(TIGLRank.UNRANKED, game.getMinimumTIGLRankAtGameStart());
        assertEquals(TIGLRank.UNRANKED, real.getPlayerTIGLRankAtGameStart());
        assertNull(dummy.getPlayerTIGLRankAtGameStart(), "dummies are not league participants");
    }

    @Test
    void snapshotStillBailsOutWhenARealPlayerCannotBeResolved() {
        Game game = tiglGame();
        game.addPlayer(HUB_MEMBER_ID, "real");
        Player unresolvable = game.addPlayer(UNRESOLVABLE_ID, "left-the-server");

        TIGLHelper.initializeTIGLGame(game, true);

        // The dummy filter must not have widened into "ignore anyone we can't resolve" - an actual player who
        // isn't a confirmable hub member still has to disable automatic rank handling.
        assertNull(game.getMinimumTIGLRankAtGameStart());
        assertNull(unresolvable.getPlayerTIGLRankAtGameStart());
    }

    @Test
    void snapshotRecordsNoRankWhenEveryPlayerIsADummy() {
        Game game = tiglGame();
        Player dummy = game.addPlayer(DUMMY_ID, "Dicecord");
        dummy.setDummy(true);

        TIGLHelper.initializeTIGLGame(game, true);

        // With nobody left to rank, the lowest-common-rank walk would otherwise return its Archon/Hero seed and
        // stamp the top of the ladder onto the game.
        assertNull(game.getMinimumTIGLRankAtGameStart());
    }
}
