package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public interface Command {

    String getName();

    default boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getName());
    }

    void execute(SlashCommandInteractionEvent event);

    void register(CommandListUpdateAction commands);

    default void postExecute(SlashCommandInteractionEvent event) {}
}
