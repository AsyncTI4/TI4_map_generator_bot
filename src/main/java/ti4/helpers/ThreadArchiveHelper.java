package ti4.helpers;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.message.logging.BotLogger;
import ti4.settings.GlobalSettings;

@UtilityClass
public class ThreadArchiveHelper {

    private static final int DEFAULT_MAX_THREAD_COUNT = 950;
    private static final int DEFAULT_CLOSE_COUNT = 25;

    public static void checkThreadLimitAndArchive(Guild guild) {
        if (guild == null) return;

        guild.retrieveActiveThreads().queue(activeThreads -> {
            int maxThreadCount = GlobalSettings.getSetting(
                    GlobalSettings.ImplementedSettings.MAX_THREAD_COUNT.toString(),
                    Integer.class,
                    DEFAULT_MAX_THREAD_COUNT);
            long threadCount = activeThreads.size();
            if (threadCount < maxThreadCount) return;

            archiveOldThreads(guild.getName(), activeThreads);
        });
    }

    public static void archiveOldThreads(Guild guild, int numberToClose) {
        List<ThreadChannel> threads = guild.retrieveActiveThreads().complete();
        archiveOldThreads(guild.getName(), threads, numberToClose);
    }

    private static void archiveOldThreads(String guildName, List<ThreadChannel> threads) {
        int numberToClose = GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.THREAD_AUTOCLOSE_COUNT.toString(),
                Integer.class,
                DEFAULT_CLOSE_COUNT);
        archiveOldThreads(guildName, threads, numberToClose);
    }

    private static void archiveOldThreads(String guildName, List<ThreadChannel> threads, int numberToClose) {
        // Sort by Latest Message ID (Oldest first)
        List<ThreadChannel> targets = threads.stream()
                .filter(c -> !c.isArchived())
                .sorted(Comparator.comparingLong(ThreadArchiveHelper::getSafeLatestMessageId))
                .sorted(Comparator.comparingInt(ThreadArchiveHelper::getArchivePriority))
                .limit(numberToClose)
                .toList();

        for (ThreadChannel thread : targets) {
            thread.getManager()
                    .setArchived(true)
                    .queue(null, e -> BotLogger.error("Failed to archive thread " + thread.getName(), e));
        }

        if (!targets.isEmpty()) {
            BotLogger.info("**" + guildName + "** Cleaned up " + targets.size() + " threads.");
        }
    }

    private static int getArchivePriority(ThreadChannel channel) {
        String name = channel.getName();
        // Define priority: 1. Non-bot threads, 2. Non-card threads, 3. Everything else
        if (name.endsWith(Constants.BOT_CHANNEL_SUFFIX)) return 3;
        if (name.startsWith(Constants.CARDS_INFO_THREAD_PREFIX)) return 2;
        return 1; // Archive first
    }

    private static long getSafeLatestMessageId(ThreadChannel channel) {
        // If no message exists, use the creation time (ID) as a fallback
        return channel.getLatestMessageIdLong() != 0 ? channel.getLatestMessageIdLong() : channel.getIdLong();
    }
}
