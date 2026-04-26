package ti4.discord.interactions.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.spring.service.deploy.ActiveLeaseService;

class AutoCompleteListener extends ListenerAdapter {

    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 1;

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) {
            return;
        }
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
