package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.autocomplete.AutoCompleteProvider;
import ti4.executors.ExecutorServiceManager;
import ti4.spring.jda.JdaService;

public class AutoCompleteListener extends ListenerAdapter {

    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 1;

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!JdaService.isReadyToReceiveCommands()
                && !event.getInteraction().getFullCommandName().startsWith("developer")) {
            return;
        }

        ExecutorServiceManager.runAsync(
                "AutoCompleteListener task",
                EXECUTION_TIME_WARNING_THRESHOLD_SECONDS,
                () -> AutoCompleteProvider.handleAutoCompleteEvent(event));
    }
}
