package ti4.discord.interactions.buttons.handlers.matchmaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.testUtils.BaseTi4Test;

class MatchmakingButtonHandlerTiglRankTest extends BaseTi4Test {

    private static final String USER_ID = "111";

    private Guild originalGuild;
    private Guild guild;
    private User user;

    @BeforeEach
    void installGuild() {
        originalGuild = JdaService.guildPrimary;
        guild = mock(Guild.class);
        JdaService.guildPrimary = guild;
        user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
    }

    @AfterEach
    void restoreGuild() {
        JdaService.guildPrimary = originalGuild;
    }

    private void giveUserRank(TIGLRank rank) {
        Role role = mock(Role.class);
        when(role.getIdLong()).thenReturn(1000L + rank.ordinal());
        when(guild.getRolesByName(rank.getName(), false)).thenReturn(List.of(role));
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of(role));
        when(guild.getMemberById(USER_ID)).thenReturn(member);
    }

    @Test
    void ministerCannotQueueAsHero() {
        giveUserRank(TIGLRank.MINISTER);

        List<String> allowed =
                MatchmakingButtonHandler.allowedTiglRanks(List.of(user), MatchmakingOptions.TIGL_RANK_OPTIONS);

        assertEquals(List.of("Unranked", "Minister"), allowed);
    }

    @Test
    void ministerPickingHeroIsNarrowedToWhatTheyHold() {
        giveUserRank(TIGLRank.MINISTER);

        List<String> allowed = MatchmakingButtonHandler.allowedTiglRanks(List.of(user), List.of("Hero", "Minister"));

        assertEquals(List.of("Minister"), allowed);
    }

    @Test
    void heroMayQueueAtEveryRank() {
        giveUserRank(TIGLRank.HERO);

        List<String> allowed =
                MatchmakingButtonHandler.allowedTiglRanks(List.of(user), MatchmakingOptions.TIGL_RANK_OPTIONS);

        assertEquals(MatchmakingOptions.TIGL_RANK_OPTIONS, allowed);
    }

    @Test
    void ministerPickingOnlyHeroFallsBackToUnranked() {
        giveUserRank(TIGLRank.MINISTER);

        List<String> allowed = MatchmakingButtonHandler.allowedTiglRanks(List.of(user), List.of("Hero"));

        assertEquals(List.of(MatchmakingOptions.UNRANKED_OPTION), allowed);
    }

    @Test
    void userWithoutAnyRankRoleOnlyGetsUnranked() {
        Member member = mock(Member.class);
        when(guild.getMemberById(USER_ID)).thenReturn(member);

        List<String> allowed =
                MatchmakingButtonHandler.allowedTiglRanks(List.of(user), MatchmakingOptions.TIGL_RANK_OPTIONS);

        assertEquals(List.of(MatchmakingOptions.UNRANKED_OPTION), allowed);
    }
}
