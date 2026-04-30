package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.context.ContextCommand;
import ti4.discord.interactions.context.ContextCommandManager;
import ti4.executors.ExecutorServiceManager;
import ti4.logging.BotLogger;
import ti4.logging.RollbarManager;
import ti4.service.game.GameNameService;

class ContextInteractionListener extends ListenerAdapter implements CommandListener {

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        onContextInteraction(event);
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        onContextInteraction(event);
    }

    private void onContextInteraction(GenericContextInteractionEvent<?> event) {
        if (!canReceiveCommands(event)) return;
        event.getInteraction().deferReply(true).queue(Consumers.nop(), BotLogger::catchRestError);
        ExecutorServiceManager.runAsync(eventToString(event), () -> process(event));
    }

    public String eventToString(GenericCommandInteractionEvent event) {
        return "ContextInteractionListener task for `" + event.getUser().getEffectiveName() + "`"
                + ": `"
                + event.getCommandString() + "`";
    }

    private void process(GenericContextInteractionEvent<?> event) {
        long processStartTime = System.currentTimeMillis();
        RollbarManager.putInteractionMetadata("context_menu", event);
        RollbarManager.put("command_name", event.getCommandString());
        RollbarManager.put("game_name", GameNameService.getGameName(event));

        ContextCommand command = ContextCommandManager.getCommand(event.getName());
        try {
            if (command.accept(event)) {
                command.preExecute(event);
                command.execute(event);
                command.postExecute(event);
            }
        } catch (Exception e) {
            command.onException(event, e);
        } finally {
            RollbarManager.clear();
        }

        warnForLongRunningCommands(event, processStartTime);
    }
}
