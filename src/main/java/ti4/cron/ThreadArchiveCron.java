package ti4.cron;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ThreadArchiveHelper;

import java.util.concurrent.TimeUnit;

@UtilityClass
public class ThreadArchiveCron {

    public static void register() {
        CronManager.schedulePeriodically(ThreadArchiveCron.class, ThreadArchiveCron::archiveThreads, 1, 1, TimeUnit.MINUTES);
    }

    private static void archiveThreads() {
        for (Guild guild : AsyncTI4DiscordBot.guilds) {
            ThreadArchiveHelper.checkThreadLimitAndArchive(guild);
        }
    }
}
