package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import ti4.helpers.ThreadArchiveHelper;
import ti4.spring.jda.JdaService;

@UtilityClass
public class ThreadArchiveCron {

    public static void register() {
        CronManager.schedulePeriodically(
                ThreadArchiveCron.class, ThreadArchiveCron::archiveThreads, 1, 1, TimeUnit.MINUTES);
    }

    private static void archiveThreads() {
        for (Guild guild : JdaService.guilds) {
            ThreadArchiveHelper.checkThreadLimitAndArchive(guild);
        }
    }
}
