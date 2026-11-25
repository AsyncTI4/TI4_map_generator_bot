package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MentionGuardTest {

    @Test
    void breaksEveryoneAndHere() {
        assertThat(MentionGuard.withoutRoleOrMassMentions("say @everyone and @here"))
                .doesNotContain("@everyone")
                .doesNotContain("@here")
                .contains("everyone")
                .contains("here");
    }

    @Test
    void replacesRoleMentionsButKeepsUserMentions() {
        assertThat(MentionGuard.withoutRoleOrMassMentions("hi <@&123> and <@456>"))
                .isEqualTo("hi @role and <@456>");
    }

    @Test
    void replacesEveryMentionKind() {
        // <@!id> is the legacy nickname form and still pings
        assertThat(MentionGuard.withoutAnyMentions("<@1> <@!2> <@&3> @everyone"))
                .isEqualTo("@someone @someone @role @\u200beveryone");
    }

    @Test
    void leavesChannelMentionsAndNullAlone() {
        assertThat(MentionGuard.withoutAnyMentions("see <#789>")).isEqualTo("see <#789>");
        assertThat(MentionGuard.withoutAnyMentions(null)).isNull();
    }
}
