package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.ThreadArchiveHelper;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

class ThreadCreateListener extends ListenerAdapter {

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) return;
        if (!event.getChannelType().isThread()) return;

        // lock per guild so we don't run it more than once
        ExecutorServiceManager.runAsyncWithLock(
                "ThreadCreateListener task on" + event.getGuild().getName(), () -> {
                    try {
                        ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
                    } catch (Exception e) {
                        BotLogger.error("Failed to check thread limit and archive on thread creation", e);
                    }
                });
    }
}
