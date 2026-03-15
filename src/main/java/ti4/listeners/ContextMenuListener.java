package ti4.listeners;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.commands.context.ContextCommand;
import ti4.commands.context.ContextCommandManager;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.DateTimeHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
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
        event.deferReply(true);
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

    private static void process(GenericContextInteractionEvent<?> event) {
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

        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long eventDelay = startTime - eventTime;

        long endTime = System.currentTimeMillis();
        long processingRuntime = endTime - startTime;

        if (eventDelay > ListenerInterface.DELAY_THRESHOLD_MILLISECONDS
                || processingRuntime > ListenerInterface.DELAY_THRESHOLD_MILLISECONDS) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(eventDelay);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingRuntime);
            String message = event.getChannel().getAsMention() + " "
                    + event.getUser().getEffectiveName() + " used: `" + event.getCommandString() + "`\n> Warning: "
                    + "This context command took over "
                    + ListenerInterface.DELAY_THRESHOLD_MILLISECONDS + "ms to respond or execute\n> "
                    + DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime)
                    + " command was issued by user\n> " + DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime)
                    + " `" + responseTime + "` to respond\n> "
                    + DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime)
                    + " `" + executionTime + "` to execute" + (processingRuntime > eventDelay ? "😲" : "");
            BotLogger.warning(new LogOrigin(event), message);
        }
    }
}
