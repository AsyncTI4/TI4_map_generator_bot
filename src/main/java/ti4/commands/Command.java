package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.SuspicionLevel;

public interface Command {

    default boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getName());
    }

    default void preExecute(SlashCommandInteractionEvent event) {}

    void execute(SlashCommandInteractionEvent event);

    default void postExecute(SlashCommandInteractionEvent event) {}

    String getName();

    default SuspicionLevel getSuspicionLevel(SlashCommandInteractionEvent event) {
        return SuspicionLevel.LITTLE;
    }

    default boolean isSuspicious(SlashCommandInteractionEvent event) {
        return getSuspicionLevel(event).isSuspicious();
    }
}
