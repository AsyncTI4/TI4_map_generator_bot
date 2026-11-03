package ti4.discord.interactions.buttons.handlers.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;

class CreateGameButtonHandlerTest {

    @Test
    void fetchMembersFromMessage_readsEveryMention() {
        Guild guild = mock(Guild.class);
        Member first = mock(Member.class);
        Member second = mock(Member.class);
        when(guild.getMemberById("111")).thenReturn(first);
        when(guild.getMemberById("222")).thenReturn(second);

        List<Member> members = CreateGameButtonHandler.fetchMembersFromMessage(
                "Players Signed Up:\n   1. <@111>\n   2. <@222>", guild);

        assertEquals(List.of(first, second), members);
    }

    @Test
    void fetchMembersFromMessage_ignoresAtSignsThatAreNotMentions() {
        // Regression: a stray "@" in the message used to be parsed as a user id and threw NumberFormatException.
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        when(guild.getMemberById("111")).thenReturn(member);

        List<Member> members = CreateGameButtonHandler.fetchMembersFromMessage(
                "Dr Hazard @ HS first\n\nPlayers Signed Up:\n   1. <@111>", guild);

        assertEquals(List.of(member), members);
        verify(guild, never()).getMemberById("Dr Hazard ");
    }

    @Test
    void fetchMembersFromMessage_acceptsNicknameMentionsAndSkipsUnknownMembers() {
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        when(guild.getMemberById("111")).thenReturn(member);

        List<Member> members = CreateGameButtonHandler.fetchMembersFromMessage("<@!111> <@999>", guild);

        assertEquals(List.of(member), members);
    }

    @Test
    void fetchMembersFromMessage_withoutMentionsLooksUpNobody() {
        Guild guild = mock(Guild.class);

        List<Member> members = CreateGameButtonHandler.fetchMembersFromMessage("no one signed up @ all", guild);

        assertTrue(members.isEmpty());
        verify(guild, never()).getMemberById(anyString());
    }
}
