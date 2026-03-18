package ti4.commands.context;

import java.util.List;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.GenericContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Type;

public abstract class MessageCommand implements ContextCommand {

    private final String name;
    private final List<Permission> perms;

    MessageCommand(String name, Permission... perms) {
        this.name = name;
        this.perms = List.of(perms);
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return Type.MESSAGE;
    }

    public List<Permission> getPermissions() {
        return perms;
    }

    public void preExecute(MessageContextInteractionEvent event) {}

    public void execute(MessageContextInteractionEvent event) {}

    public void postExecute(MessageContextInteractionEvent event) {}

    public void preExecute(GenericContextInteractionEvent<?> event) {
        if (event instanceof MessageContextInteractionEvent msgEvent) preExecute(msgEvent);
    }

    public void execute(GenericContextInteractionEvent<?> event) {
        if (event instanceof MessageContextInteractionEvent msgEvent) execute(msgEvent);
    }

    public void postExecute(GenericContextInteractionEvent<?> event) {
        if (event instanceof MessageContextInteractionEvent msgEvent) postExecute(msgEvent);
    }
}
