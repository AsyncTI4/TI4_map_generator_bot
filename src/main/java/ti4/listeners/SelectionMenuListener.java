package ti4.listeners;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.AsyncTI4DiscordBot;
import ti4.listeners.context.SelectionMenuContext;
import ti4.message.BotLogger;
import ti4.selections.SelectionMenuProvider;

public class SelectionMenuListener extends ListenerAdapter {

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to receive selections.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        executorService.submit(() -> handleStringSelectionInteraction(event));
    }

    private void handleStringSelectionInteraction(StringSelectInteractionEvent event) {
        try {
            SelectionMenuContext context = new SelectionMenuContext(event);
            if (context.isValid()) {
                SelectionMenuProvider.resolveSelectionMenu(context);
            }
        } catch (Exception e) {
            String message = "Selection Menu issue in event: " + event.getComponentId() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.log(message, e);
        }
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
