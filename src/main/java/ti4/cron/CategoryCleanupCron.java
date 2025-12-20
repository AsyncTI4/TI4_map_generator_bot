package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.spring.jda.JdaService;

@UtilityClass
public class CategoryCleanupCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CategoryCleanupCron.class, CategoryCleanupCron::cleanupCategories, 6, 24, TimeUnit.HOURS);
    }

    private static void cleanupCategories() {
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
