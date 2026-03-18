package ti4.commands.context;

import java.util.List;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.GenericContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Type;

public abstract class UserCommand implements ContextCommand {

    private final String name;
    private final List<Permission> perms;

    UserCommand(String name, Permission... perms) {
        this.name = name;
        this.perms = List.of(perms);
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return Type.USER;
    }

    public List<Permission> getPermissions() {
        return perms;
    }

    public void preExecute(UserContextInteractionEvent event) {}

    public void execute(UserContextInteractionEvent event) {}

    public void postExecute(UserContextInteractionEvent event) {}

    public void preExecute(GenericContextInteractionEvent<?> event) {
        if (event instanceof UserContextInteractionEvent userEvent) preExecute(userEvent);
    }

    public void execute(GenericContextInteractionEvent<?> event) {
        if (event instanceof UserContextInteractionEvent userEvent) execute(userEvent);
    }

    public void postExecute(GenericContextInteractionEvent<?> event) {
        if (event instanceof UserContextInteractionEvent userEvent) postExecute(userEvent);
    }
}
