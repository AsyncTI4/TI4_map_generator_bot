package ti4.helpers;

import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.executors.ExecutorServiceManager;
import ti4.message.logging.BotLogger;
import ti4.settings.GlobalSettings;

public class ThreadArchiveHelper {

    private static final int DEFAULT_MAX_THREAD_COUNT = 975;
    private static final int DEFAULT_CLOSE_COUNT = 25;

    public static void checkThreadLimitAndArchive(Guild guild) {
        if (guild == null) return;

        ExecutorServiceManager.runAsyncIfNotRunning("ThreadArchiveHelper task for `" + guild.getName() + "`", () -> {
            try {
                long threadCount = guild.getThreadChannels().stream()
                        .filter(c -> !c.isArchived())
                        .count();
                int closeCount = GlobalSettings.getSetting(
                        GlobalSettings.ImplementedSettings.THREAD_AUTOCLOSE_COUNT.toString(),
                        Integer.class,
                        DEFAULT_CLOSE_COUNT);
                int maxThreadCount = GlobalSettings.getSetting(
                        GlobalSettings.ImplementedSettings.MAX_THREAD_COUNT.toString(),
                        Integer.class,
                        DEFAULT_MAX_THREAD_COUNT);

                if (threadCount > maxThreadCount) {
                    BotLogger.info("**" + guild.getName() + "** Max Threads Reached (" + threadCount + " out of  "
                            + maxThreadCount + ") - Archiving " + closeCount + " threads");
                    archiveOldThreads(guild, closeCount);
                }
            } catch (Exception e) {
                BotLogger.error("Error in checkThreadLimitAndArchive for " + guild.getName(), e);
            }
        });
    }

    public static void archiveOldThreads(Guild guild, Integer threadCount) {
        // Try gathering all threads that are not bot-map or cards-info threads
        List<ThreadChannel> threadChannels = guild.getThreadChannels().stream()
                .filter(c -> c.getLatestMessageIdLong() != 0 && !c.isArchived())
                .filter(threadChannel -> !threadChannel.getName().contains("bot-map-updates")
                        && !threadChannel.getName().contains("cards-info"))
                .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
                .limit(threadCount)
                .toList();

        // If there are fewer channels in the list than requested to close, include cards-info threads
        if (threadChannels.size() < (threadCount - 1)) {
            threadChannels = guild.getThreadChannels().stream()
                    .filter(c -> c.getLatestMessageIdLong() != 0 && !c.isArchived())
                    .filter(threadChannel -> !threadChannel.getName().contains("bot-map-updates"))
                    .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
                    .limit(threadCount)
                    .toList();
        }

        for (ThreadChannel threadChannel : threadChannels) {
            threadChannel.getManager().setArchived(true).queue(null, BotLogger::catchRestError);
        }
    }
}
