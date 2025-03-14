package ti4.service.fow;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;

public class UserOverridenGenericInteractionCreateEvent extends GenericInteractionCreateEvent {

    private final User overriddenUser;
        
    public UserOverridenGenericInteractionCreateEvent(GenericInteractionCreateEvent event, User overriddenUser) {
        super(event.getJDA(), event.getResponseNumber(), event.getInteraction());
        this.overriddenUser = overriddenUser;
    }

    @NotNull
    @Override
    public User getUser() {
        return overriddenUser;
    }
}