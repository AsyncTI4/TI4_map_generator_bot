package ti4.commands.context;

import java.util.List;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.GenericContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Type;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;

public interface ContextCommand<T extends GenericContextInteractionEvent<?>> extends Command<T> {

    Type getType();

    List<Permission> getPermissions();

    @Override
    default boolean accept(T event) {
        return Command.super.accept(event);
    }

    default void register(CommandListUpdateAction commands) {
        var command = Commands.context(getType(), getName())
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(getPermissions()));
        commands.addCommands(command);
    }

    default void registerSearchCommands(CommandListUpdateAction commands) {
        return;
    }
}
