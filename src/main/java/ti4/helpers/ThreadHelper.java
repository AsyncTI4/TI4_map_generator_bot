package ti4.helpers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import ti4.AsyncTI4DiscordBot;
import ti4.message.BotLogger;
import ti4.settings.GlobalSettings;

@UtilityClass
public class ThreadHelper {

    public static void checkThreadLimitAndArchive(Guild guild) {
        if (guild == null) return;

        AsyncTI4DiscordBot.runAsync("Archive threads task.", () -> {
            try {
                long threadCount = guild.getThreadChannels().stream().filter(c -> !c.isArchived()).count();
                int closeCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.THREAD_AUTOCLOSE_COUNT.toString(), Integer.class, 25);
                int maxThreadCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.MAX_THREAD_COUNT.toString(), Integer.class, 975);

                if (threadCount > maxThreadCount) {
                    BotLogger.log("**" + guild.getName() + "** Max Threads Reached (" + threadCount + " out of  " + maxThreadCount + ") - Archiving " + closeCount + " threads");
                    ThreadArchiveHelper.archiveOldThreads(guild, closeCount);
                }
            } catch (Exception e) {
                BotLogger.log("Error in checkThreadLimitAndArchive", e);
            }
        });
    }
}
