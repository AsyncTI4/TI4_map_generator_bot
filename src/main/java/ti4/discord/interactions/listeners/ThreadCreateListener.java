package ti4.discord.interactions.listeners;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.ThreadArchiveHelper;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

class ThreadCreateListener extends ListenerAdapter {

    private static final Map<String, Instant> guildToLatestArchive = new ConcurrentHashMap<>();
    private static final int GUILD_THREAD_ARCHIVE_COOLDOWN_SECONDS = 5;

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) return;
        if (!event.getChannelType().isThread()) return;
        // These are archived already, and if this listener fails it could death spiral on Stack Trace threads
        if ("Stack Trace".equalsIgnoreCase(event.getChannel().getName())) return;

        if (isOnCooldown(event.getGuild().getId())) return;

        // lock per guild so we don't run it more than once
        ExecutorServiceManager.runAsyncWithLock(
                "ThreadCreateListener task on " + event.getGuild().getName(), () -> {
                    try {
                        ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
                    } catch (Exception e) {
                        BotLogger.error("Failed to check thread limit and archive on thread creation", e);
                    }
                });
    }

    private static boolean isOnCooldown(String guildId) {
        AtomicBoolean onCooldown = new AtomicBoolean(true);
        guildToLatestArchive.compute(guildId, (_, existingTime) -> {
            Instant now = Instant.now();
            if (existingTime == null
                    || existingTime
                            .plusSeconds(GUILD_THREAD_ARCHIVE_COOLDOWN_SECONDS)
                            .isBefore(now)) {
                onCooldown.set(false);
                return now;
            }
            return existingTime;
        });
        return onCooldown.get();
    }
}
