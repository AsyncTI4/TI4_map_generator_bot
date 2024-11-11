package ti4.commands;

import java.util.Collection;
import java.util.Collections;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public interface Command {

    default boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionId());
    }

    default void preExecute(SlashCommandInteractionEvent event) {}

    default void execute(SlashCommandInteractionEvent event) {
        for (Subcommand subcommand : getSubcommands()) {
            if (subcommand.accept(event)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subcommand.postExecute(event);
                break;
            }
        }
    }

    default void postExecute(SlashCommandInteractionEvent event) {
        event.getHook().deleteOriginal().submit();
    }

    String getActionId();

    String getActionDescription();

    default Collection<Subcommand> getSubcommands() {
        return Collections.emptyList();
    }

    default void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionId(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}
