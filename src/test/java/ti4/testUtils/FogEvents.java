package ti4.testUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;

public final class FogEvents {

    private FogEvents() {}

    public static GenericInteractionCreateEvent eventIn(String userId, String channelName) {
        GenericInteractionCreateEvent event = eventOf(userId);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        when(channel.getName()).thenReturn(channelName);
        when(event.getChannel()).thenReturn(channel);
        when(event.getMessageChannel()).thenReturn(channel);
        return event;
    }

    public static GenericInteractionCreateEvent eventInThreadUnder(String userId, String parentChannelName) {
        GenericInteractionCreateEvent event = eventOf(userId);
        MessageChannelUnion thread =
                mock(MessageChannelUnion.class, withSettings().extraInterfaces(ThreadChannel.class));
        when(thread.getName()).thenReturn("a-thread");
        IThreadContainerUnion parent = mock(IThreadContainerUnion.class);
        when(parent.getName()).thenReturn(parentChannelName);
        when(((ThreadChannel) thread).getParentChannel()).thenReturn(parent);
        when(event.getChannel()).thenReturn(thread);
        when(event.getMessageChannel()).thenReturn(thread);
        return event;
    }

    private static GenericInteractionCreateEvent eventOf(String userId) {
        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Member member = mock(Member.class);
        when(member.getUser()).thenReturn(user);
        when(event.getUser()).thenReturn(user);
        when(event.getMember()).thenReturn(member);
        return event;
    }
}
