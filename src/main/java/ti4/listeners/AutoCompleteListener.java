package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.service.JdaService;
import ti4.autocomplete.AutoCompleteProvider;
import ti4.executors.ExecutorServiceManager;

public class AutoCompleteListener extends ListenerAdapter {

    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 1;

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!JdaService.isReadyToReceiveCommands()
                && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.replyChoice("Please try again in a moment. The bot is not ready to serve AutoComplete.", 0)
                    .queue();
            return;
        }

        ExecutorServiceManager.runAsync(
                "AutoCompleteListener task",
                EXECUTION_TIME_WARNING_THRESHOLD_SECONDS,
                () -> AutoCompleteProvider.handleAutoCompleteEvent(event));
    }
}
