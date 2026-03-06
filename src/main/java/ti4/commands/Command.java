package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;

public interface Command {

    default boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getName());
    }

    default void preExecute(SlashCommandInteractionEvent event) {}

    void execute(SlashCommandInteractionEvent event);

    default void postExecute(SlashCommandInteractionEvent event) {}

    String getName();

    default boolean isSuspicious(SlashCommandInteractionEvent event) {
        return false;
    }

    default void onException(SlashCommandInteractionEvent event, Throwable throwable) {
        String messageText = "Error trying to execute command: " + getName();
        String errorMessage = ExceptionUtils.getMessage(throwable);
        event.getHook().editOriginal(errorMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        BotLogger.error(new LogOrigin(event), messageText, throwable);
    }
}
