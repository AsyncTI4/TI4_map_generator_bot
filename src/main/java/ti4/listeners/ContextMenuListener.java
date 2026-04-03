package ti4.listeners;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.commands.context.ContextCommand;
import ti4.commands.context.ContextCommandManager;
import ti4.executors.ExecutorServiceManager;
import ti4.message.logging.BotLogger;
import ti4.service.game.GameNameService;

public class ContextMenuListener extends ListenerAdapter implements ListenerInterface {

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        onContextInteraction(event);
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        onContextInteraction(event);
    }

    private void onContextInteraction(GenericContextInteractionEvent<?> event) {
        if (!receiveCommands(event)) return;
        event.getInteraction().deferReply(true).queue(Consumers.nop(), BotLogger::catchRestError);
        queue(event);
    }

    private void queue(GenericContextInteractionEvent<?> event) {
        String gameName = GameNameService.getGameName(event);
        String lock = gameName == null ? "async" : gameName;
        ExecutorServiceManager.runAsync(
                eventToString(event, gameName), lock, event.getMessageChannel(), () -> process(event));
    }

    public String eventToString(GenericCommandInteractionEvent event, String gameName) {
        return "ContextMenuListener task for `" + event.getUser().getEffectiveName() + "`"
                + (gameName == null ? "" : " in `" + gameName + "`")
                + ": `"
                + event.getCommandString() + "`";
    }

    private void process(GenericContextInteractionEvent<?> event) {
        long startTime = System.currentTimeMillis();

        ContextCommand command = ContextCommandManager.getCommand(event.getName());
        try {
            if (command.accept(event)) {
                command.preExecute(event);
                command.execute(event);
                command.postExecute(event);
            }
        } catch (Exception e) {
            command.onException(event, e);
        }

        warnForLongRunningCommands(event, startTime);
    }
}
