package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.ThreadArchiveHelper;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

class ThreadCreateListener extends ListenerAdapter {

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) return;
        if (!event.getChannelType().isThread()) return;

        try {
            ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
        } catch (Exception e) {
            BotLogger.error("Failed to check thread limit and archive on thread creation", e);
        }
    }
}
