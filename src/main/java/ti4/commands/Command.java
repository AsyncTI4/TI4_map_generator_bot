package ti4.commands;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;

public interface Command<T extends GenericInteractionCreateEvent> {

    default boolean accept(T event) {
        if (event instanceof SlashCommandInteractionEvent slashEvent)
            return slashEvent.getName().equals(getName());
        if (event instanceof MessageContextInteractionEvent msgEvent)
            return msgEvent.getName().equals(getName());
        if (event instanceof UserContextInteractionEvent usrEvent)
            return usrEvent.getName().equals(getName());
        else return false;
    }

    default void preExecute(T event) {}

    void execute(T event);

    default void postExecute(T event) {}

    String getName();

    default boolean isSuspicious(T event) {
        return false;
    }

    default void register(CommandListUpdateAction update) {
        return;
    }

    default void registerSearchCommands(CommandListUpdateAction update) {
        return;
    }

    default void onException(T event, Throwable throwable) {
        String messageText = "Error trying to execute command: " + getName();
        String errorMessage = ExceptionUtils.getMessage(throwable);
        if (event instanceof SlashCommandInteractionEvent slashEvent)
            slashEvent.getHook().editOriginal(errorMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        BotLogger.error(new LogOrigin(event), messageText, throwable);
    }
}
