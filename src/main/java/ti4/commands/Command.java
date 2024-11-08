package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public interface Command {

    String getActionID();

    default boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    void execute(SlashCommandInteractionEvent event);

    void registerCommands(CommandListUpdateAction commands);

    default void postExecute(SlashCommandInteractionEvent event) {
        event.getHook().deleteOriginal().submit();
    }
}
