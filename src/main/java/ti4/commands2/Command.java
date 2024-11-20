package ti4.commands2;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

//TODO REMOVE EXTENSION WHEN READY
public interface Command extends ti4.commands.Command {

    default boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getName());
    }

    default void preExecute(SlashCommandInteractionEvent event) {}

    void execute(SlashCommandInteractionEvent event);

    default void postExecute(SlashCommandInteractionEvent event) {}

    String getName();
}
