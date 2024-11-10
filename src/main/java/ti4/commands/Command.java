package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public interface Command {

    String getActionId();

    default boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionId());
    }

    void registerCommands(CommandListUpdateAction commands);

    default void preExecute(SlashCommandInteractionEvent event) {}

    void execute(SlashCommandInteractionEvent event);

    default void postExecute(SlashCommandInteractionEvent event) {
        event.getHook().deleteOriginal().submit();
    }
}
