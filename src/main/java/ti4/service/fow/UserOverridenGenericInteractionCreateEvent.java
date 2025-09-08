package ti4.service.fow;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;

public class UserOverridenGenericInteractionCreateEvent extends GenericInteractionCreateEvent {

    private final Member overriddenMember;

    public UserOverridenGenericInteractionCreateEvent(GenericInteractionCreateEvent event, Member overriddenMember) {
        super(event.getJDA(), event.getResponseNumber(), event.getInteraction());
        this.overriddenMember = overriddenMember;
    }

    @NotNull
    @Override
    public Member getMember() {
        return overriddenMember;
    }

    @NotNull
    @Override
    public User getUser() {
        return overriddenMember.getUser();
    }
}
