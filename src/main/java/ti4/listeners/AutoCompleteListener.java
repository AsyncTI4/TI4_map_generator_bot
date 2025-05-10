package ti4.listeners;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.autocomplete.AutoCompleteProvider;
import ti4.executors.ExecutorManager;

public class AutoCompleteListener extends ListenerAdapter {

    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 1;

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.replyChoice("Please try again in a moment. The bot is not ready to serve AutoComplete.", 0).queue();
            return;
        }

        ExecutorManager.runAsync("AutoCompleteListener task", EXECUTION_TIME_WARNING_THRESHOLD_SECONDS,
            () -> AutoCompleteProvider.handleAutoCompleteEvent(event));
    }
}
