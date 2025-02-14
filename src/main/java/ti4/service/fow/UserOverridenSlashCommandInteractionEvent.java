package ti4.service.fow;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class UserOverridenSlashCommandInteractionEvent extends SlashCommandInteractionEvent {

    private final User overriddenUser;

    public UserOverridenSlashCommandInteractionEvent(SlashCommandInteractionEvent event, User overriddenUser) {
        super(event.getJDA(), event.getResponseNumber(), event.getInteraction());
        this.overriddenUser = overriddenUser;
    }

    @NotNull
    @Override
    public User getUser() {
        return overriddenUser;
    }
}
