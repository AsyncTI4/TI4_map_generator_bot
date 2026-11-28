package ti4.ai.actuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.SelfUser;
import org.junit.jupiter.api.Test;

class AiActorTest {

    private static final String SEAT_ID = "7100000123456789";

    // The stand-in event reports the AI seat's id, so ListenerContext resolves the AI seat as the presser and
    // the FFCC_<faction>_ owner check passes; everything else is the bot's own member and user.
    @Test
    void reportsTheSeatIdentityAndDelegatesEverythingElseToTheBot() {
        Guild guild = mock(Guild.class);
        SelfMember self = mock(SelfMember.class);
        SelfUser selfUser = mock(SelfUser.class);
        when(guild.getSelfMember()).thenReturn(self);
        when(self.getUser()).thenReturn(selfUser);
        when(self.getGuild()).thenReturn(guild);
        when(selfUser.isBot()).thenReturn(true);

        Member actor = AiActor.member(guild, SEAT_ID, "Nekro AI");

        assertThat(actor.getId()).isEqualTo(SEAT_ID);
        assertThat(actor.getIdLong()).isEqualTo(Long.parseLong(SEAT_ID));
        assertThat(actor.getEffectiveName()).isEqualTo("Nekro AI");
        assertThat(actor.getUser().getId()).isEqualTo(SEAT_ID);
        assertThat(actor.getUser().getName()).isEqualTo("Nekro AI");
        assertThat(actor.getGuild()).isSameAs(guild);
        assertThat(actor.getUser().isBot()).isTrue();
    }
}
