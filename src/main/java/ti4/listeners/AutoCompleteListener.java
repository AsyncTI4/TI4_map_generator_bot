package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.autocomplete.AutoCompleteProvider;

public class AutoCompleteListener extends ListenerAdapter {

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.replyChoice("Please try again in a moment. The bot is not ready to serve AutoComplete.", 0).queue();
            return;
        }
        executorService.submit(() -> AutoCompleteProvider.handleAutoCompleteEvent(event));
    }

    public static boolean shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(20, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                return false;
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
