package ti4.helpers;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.executors.ExecutorManager;
import ti4.message.BotLogger;
import ti4.settings.GlobalSettings;

public class ThreadArchiveHelper {

    public static void checkThreadLimitAndArchive(Guild guild) {
        if (guild == null) return;

        ExecutorManager.runAsync("ThreadArchiveHelper task.", () -> {
            try {
                long threadCount = guild.getThreadChannels().stream().filter(c -> !c.isArchived()).count();
                int closeCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.THREAD_AUTOCLOSE_COUNT.toString(), Integer.class, 25);
                int maxThreadCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.MAX_THREAD_COUNT.toString(), Integer.class, 975);

                if (threadCount > maxThreadCount) {
                    BotLogger.info(new BotLogger.LogMessageOrigin(guild), "**" + guild.getName() + "** Max Threads Reached (" + threadCount + " out of  " + maxThreadCount + ") - Archiving " + closeCount + " threads");
                    archiveOldThreads(guild, closeCount);
                }
            } catch (Exception e) {
                BotLogger.error(new BotLogger.LogMessageOrigin(guild), "Error in checkThreadLimitAndArchive", e);
            }
        });
    }

    public static void archiveOldThreads(Guild guild, Integer threadCount) {
        // Try gathering all threads that are not bot-map or cards-info threads
        List<ThreadChannel> threadChannels = guild.getThreadChannels().stream()
            .filter(c -> c.getLatestMessageIdLong() != 0 && !c.isArchived())
            .filter(threadChannel -> !threadChannel.getName().contains("bot-map-updates") && !threadChannel.getName().contains("cards-info"))
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
            threadChannel.getManager()
                .setArchived(true)
                .queue(null, BotLogger::catchRestError);
        }
    }
}
