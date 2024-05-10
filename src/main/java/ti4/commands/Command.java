package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public interface Command {
    String getActionID();

    //If command can be executed for given command text
    boolean accept(SlashCommandInteractionEvent event);

    //Command action execution method
    void execute(SlashCommandInteractionEvent event);

    void registerCommands(CommandListUpdateAction commands);

    default void postExecute(SlashCommandInteractionEvent event) {
        event.getHook().deleteOriginal().submit();
    }
}
