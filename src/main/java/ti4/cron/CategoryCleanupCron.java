package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CategoryCleanupCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                CategoryCleanupCron.class, CategoryCleanupCron::cleanupCategories, 5, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanupCategories() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        JdaService.guilds.forEach(guild -> guild.getCategories().stream()
                .filter(category -> category.getName().startsWith("PBD #"))
                .filter(category -> category.getChannels().isEmpty())
                .forEach(category -> {
                    BotLogger.info("**CategoryCleanupCron** Deleted empty category: " + category.getName()
                            + " on guild: " + guild.getName());
                    category.delete().queue(Consumers.nop(), BotLogger::catchRestError);
                }));
    }
}
