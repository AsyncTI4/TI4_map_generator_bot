package ti4.discord.interactions.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.service.message.MessageDeletionService;

class DeletionListener extends ListenerAdapter {

    @Override
    public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
        if (!validateEvent(event)) return;

        ExecutorServiceManager.runAsync(
                "DeletionListener task", () -> MessageDeletionService.handleMessageDelete(event));
    }

    private static boolean validateEvent(MessageDeleteEvent event) {
        if (!JdaService.isReadyToReceiveCommands()) return false;

        String eventGuild = event.getGuild().getId();
        return JdaService.isValidGuild(eventGuild);
    }
}
