package ti4.discord.interactions.buttons.handlers.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CreateGameButtonHandlerTest {

    @Test
    void buildJoinAnnouncementAppendsCongratulationLinesInSignupOrder() {
        Member joiningMember = member("joiner");
        Member beta = member("beta");
        Member alpha = member("alpha");

        String announcement = CreateGameButtonHandler.buildJoinAnnouncement(
                "<@joiner> joined the game.",
                joiningMember,
                List.of(beta, alpha),
                Map.of("alpha", 1, "beta", 2));

        assertThat(announcement)
                .isEqualTo(
                        "<@joiner> joined the game.\n"
                                + "🎉 Congrats to <@joiner> and <@beta> on their 3rd game together!\n"
                                + "🎉 Congrats to <@joiner> and <@alpha> on their 2nd game together!");
    }

    @Test
    void buildJoinAnnouncementReturnsOriginalMessageWhenThereAreNoSharedGames() {
        Member joiningMember = member("joiner");
        Member alpha = member("alpha");

        assertThat(CreateGameButtonHandler.buildJoinAnnouncement(
                        "<@joiner> joined the game.", joiningMember, List.of(alpha), Map.of()))
                .isEqualTo("<@joiner> joined the game.");
    }

    private static Member member(String id) {
        Member member = Mockito.mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getAsMention()).thenReturn("<@" + id + ">");
        return member;
    }
}
